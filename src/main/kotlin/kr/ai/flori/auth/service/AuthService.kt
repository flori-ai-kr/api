package kr.ai.flori.auth.service

import kr.ai.flori.auth.dto.LoginRequest
import kr.ai.flori.auth.dto.SignupRequest
import kr.ai.flori.auth.dto.TokenResponse
import kr.ai.flori.auth.dto.UserResponse
import kr.ai.flori.auth.entity.RefreshToken
import kr.ai.flori.auth.entity.User
import kr.ai.flori.auth.oauth.SocialOAuthClient
import kr.ai.flori.auth.oauth.SocialUserInfo
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
    // 제공자 일반화: 빈 이름(KAKAO/GOOGLE/NAVER)을 키로 소셜 클라이언트 주입. 새 제공자는 빈 추가만으로 확장.
    private val socialClients: Map<String, SocialOAuthClient>,
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

    /**
     * 소셜 로그인 일반화 진입점. provider(KAKAO/GOOGLE/NAVER)로 클라이언트를 선택해 인증코드를 검증하고,
     * 없으면 가입 후 토큰을 발급한다. 네이버는 state가 필수다.
     */
    @Transactional
    fun oauthLogin(
        provider: String,
        code: String,
        redirectUri: String,
        state: String?,
    ): TokenResponse {
        val client =
            socialClients[provider]
                ?: throw AppException(ErrorCode.VALIDATION, "지원하지 않는 소셜 제공자입니다: $provider")
        val info = client.authenticate(code, redirectUri, state)
        val user = findOrCreateSocialUser(info)
        if (!user.isActive) {
            throw AppException(ErrorCode.FORBIDDEN, "비활성화된 계정입니다")
        }
        return issueTokens(user)
    }

    private fun findOrCreateSocialUser(info: SocialUserInfo): User {
        userRepository.findByProviderAndProviderId(info.provider, info.providerId)?.let { return it }
        return try {
            userRepository
                .saveAndFlush(
                    User(
                        // 신규 소셜 가입: 제공자 이메일이 있고 아직 미사용일 때만 채운다.
                        // 충돌(이미 다른 계정 사용 중) 시 email=null 유지 — 이메일 선점 공격 방지를 위해 자동 병합하지 않는다.
                        email = info.email?.takeIf { !userRepository.existsByEmail(it) },
                        provider = info.provider,
                        providerId = info.providerId,
                        name = info.nickname,
                    ),
                ).also { seeder.seedForNewUser(requireNotNull(it.id)) }
        } catch (_: DataIntegrityViolationException) {
            // 동시 첫 로그인 경쟁: 상대 요청이 먼저 INSERT 커밋 → 재조회해 기존 사용자 반환(멱등)
            userRepository.findByProviderAndProviderId(info.provider, info.providerId)
                ?: throw AppException(ErrorCode.INTERNAL, "소셜 사용자 생성에 실패했습니다")
        }
    }

    /** 현재 사용자 조회. TenantContext에서 받은 userId로 격리. */
    @Transactional(readOnly = true)
    fun me(userId: Long): UserResponse {
        val user =
            userRepository
                .findById(userId)
                .orElseThrow { AppException(ErrorCode.UNAUTHORIZED) }
        return UserResponse(id = userId, email = user.email, name = user.name)
    }

    /**
     * 소셜 가입 후 이메일 보완. 형식 검증은 컨트롤러(@Valid)에서, 여기서는 중복 검사 후 저장한다.
     * 본인이 이미 같은 이메일을 갖고 있으면 멱등 통과, 다른 사용자가 사용 중이면 DUPLICATE(409).
     */
    @Transactional
    fun updateEmail(
        userId: Long,
        email: String,
    ): UserResponse {
        val user =
            userRepository
                .findById(userId)
                .orElseThrow { AppException(ErrorCode.UNAUTHORIZED) }
        if (email != user.email && userRepository.existsByEmail(email)) {
            throw AppException(ErrorCode.DUPLICATE, "이미 사용 중인 이메일입니다")
        }
        user.email = email
        userRepository.save(user)
        return UserResponse(id = userId, email = user.email, name = user.name)
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
