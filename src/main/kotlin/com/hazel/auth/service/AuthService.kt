package com.hazel.auth.service

import com.hazel.auth.dto.LoginRequest
import com.hazel.auth.dto.SignupRequest
import com.hazel.auth.dto.TokenResponse
import com.hazel.auth.entity.RefreshToken
import com.hazel.auth.entity.User
import com.hazel.auth.repository.RefreshTokenRepository
import com.hazel.auth.repository.UserRepository
import com.hazel.common.error.AppException
import com.hazel.common.error.ErrorCode
import com.hazel.common.security.JwtProperties
import com.hazel.common.security.JwtTokenProvider
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64

/**
 * 인증 서비스: 가입(기본 설정 시드 포함) · 로그인 · refresh 회전 · 로그아웃.
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
        if (user == null || !passwordEncoder.matches(request.password, user.passwordHash)) {
            throw AppException(ErrorCode.INVALID_CREDENTIALS)
        }
        if (!user.isActive) {
            throw AppException(ErrorCode.FORBIDDEN, "비활성화된 계정입니다")
        }
        return issueTokens(user)
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
        val accessToken = tokenProvider.createAccessToken(userId, user.email)
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
