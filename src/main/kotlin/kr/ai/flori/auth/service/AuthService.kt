package kr.ai.flori.auth.service

import kr.ai.flori.auth.dto.LoginRequest
import kr.ai.flori.auth.dto.SignupRequest
import kr.ai.flori.auth.dto.TokenResponse
import kr.ai.flori.auth.entity.RefreshToken
import kr.ai.flori.auth.entity.User
import kr.ai.flori.auth.oauth.KakaoOAuthClient
import kr.ai.flori.auth.oauth.KakaoUserInfo
import kr.ai.flori.auth.repository.RefreshTokenRepository
import kr.ai.flori.auth.repository.UserRepository
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.ErrorCode
import kr.ai.flori.common.security.JwtProperties
import kr.ai.flori.common.security.JwtTokenProvider
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64

/**
 * 인증 서비스: 가입(기본 설정 시드 포함) · 로그인 · 소셜 로그인 · refresh 회전 · 로그아웃.
 *
 * - 비밀번호는 BCrypt 해시로만 저장(평문 로깅 금지).
 * - access는 자체 JWT(짧은 TTL), refresh는 불투명 난수 + DB에 SHA-256 해시 저장.
 * - refresh 회전: 사용 시 기존 토큰 무효화 후 새 토큰 발급.
 */
@Service
class AuthService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenProvider: JwtTokenProvider,
    private val jwtProperties: JwtProperties,
    private val seeder: DefaultDataSeeder,
    private val kakaoClient: KakaoOAuthClient,
) {
    private val secureRandom = SecureRandom()

    @Transactional
    fun signup(request: SignupRequest): TokenResponse {
        if (userRepository.existsByEmail(request.email)) {
            throw AppException(ErrorCode.DUPLICATE, "이미 가입된 이메일입니다")
        }
        // saveAndFlush: 시더의 raw JDBC INSERT가 같은 트랜잭션에서 user FK를 참조하므로 즉시 flush
        val user =
            userRepository.saveAndFlush(
                User(
                    email = request.email,
                    passwordHash = passwordEncoder.encode(request.password),
                    name = request.name,
                ),
            )
        seeder.seedForNewUser(requireNotNull(user.id))
        return issueTokens(user)
    }

    @Transactional
    fun login(request: LoginRequest): TokenResponse {
        // 이메일 미존재와 비밀번호 불일치를 동일 응답으로 처리(사용자 열거 방지)
        val user = userRepository.findByEmail(request.email)
        val hash = user?.passwordHash
        if (user == null || hash == null || !passwordEncoder.matches(request.password, hash)) {
            throw AppException(ErrorCode.INVALID_CREDENTIALS)
        }
        if (!user.isActive) {
            throw AppException(ErrorCode.FORBIDDEN, "비활성화된 계정입니다")
        }
        return issueTokens(user)
    }

    @Transactional
    fun oauthLogin(
        code: String,
        redirectUri: String,
    ): TokenResponse {
        val info = kakaoClient.authenticate(code, redirectUri)
        val user = findOrCreateKakaoUser(info)
        if (!user.isActive) {
            throw AppException(ErrorCode.FORBIDDEN, "비활성화된 계정입니다")
        }
        return issueTokens(user)
    }

    private fun findOrCreateKakaoUser(info: KakaoUserInfo): User {
        userRepository.findByProviderAndProviderId("KAKAO", info.providerId)?.let { return it }
        return try {
            userRepository
                .saveAndFlush(
                    User(
                        provider = "KAKAO",
                        providerId = info.providerId,
                        name = info.nickname,
                    ),
                ).also { seeder.seedForNewUser(requireNotNull(it.id)) }
        } catch (_: DataIntegrityViolationException) {
            // 동시 첫 로그인 경쟁: 상대 요청이 먼저 INSERT 커밋 → 재조회해 기존 사용자 반환(멱등)
            userRepository.findByProviderAndProviderId("KAKAO", info.providerId)
                ?: throw AppException(ErrorCode.INTERNAL, "소셜 사용자 생성에 실패했습니다")
        }
    }

    @Transactional
    fun refresh(rawRefreshToken: String): TokenResponse {
        val stored =
            refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken))
                ?: throw AppException(ErrorCode.INVALID_TOKEN)
        if (stored.revoked || stored.expiresAt.isBefore(Instant.now())) {
            throw AppException(ErrorCode.INVALID_TOKEN)
        }
        val user =
            userRepository
                .findById(stored.userId)
                .orElseThrow { AppException(ErrorCode.INVALID_TOKEN) }

        // 회전: 사용된 refresh 토큰 무효화 후 재발급
        stored.revoked = true
        refreshTokenRepository.save(stored)
        return issueTokens(user)
    }

    @Transactional
    fun logout(rawRefreshToken: String) {
        refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken))?.let { token ->
            token.revoked = true
            refreshTokenRepository.save(token)
        }
    }

    private fun issueTokens(user: User): TokenResponse {
        val userId = requireNotNull(user.id)
        val accessToken = tokenProvider.createAccessToken(userId, user.email ?: "")
        val rawRefresh = generateRefreshToken()
        refreshTokenRepository.save(
            RefreshToken(
                userId = userId,
                tokenHash = hashToken(rawRefresh),
                expiresAt = Instant.now().plusSeconds(jwtProperties.refreshTtlSeconds),
            ),
        )
        return TokenResponse(
            accessToken = accessToken,
            refreshToken = rawRefresh,
            expiresIn = jwtProperties.accessTtlSeconds,
        )
    }

    private fun generateRefreshToken(): String {
        val bytes = ByteArray(REFRESH_TOKEN_BYTES)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hashToken(token: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val REFRESH_TOKEN_BYTES = 32
    }
}
