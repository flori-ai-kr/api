package kr.ai.flori.common.security

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.Date
import javax.crypto.SecretKey

/**
 * access 토큰(자체 JWT, HS256) 발급/검증. refresh 토큰은 불투명 난수로 별도 관리(AuthService).
 *
 * registerToken: 소셜 인증은 끝났으나 아직 User 행이 없는 "가입 대기" 상태를 담는 단기(5분) JWT.
 * 온보딩 완료(register/complete) 시점에만 User를 생성하므로, 그 사이의 신원을 비밀번호 없이 전달한다.
 */
@Component
class JwtTokenProvider(
    private val properties: JwtProperties,
    @Value("\${spring.profiles.active:local}") private val activeProfile: String,
) {
    // 운영 안전장치: 비-로컬 프로필에서 깃에 박힌 기본 시크릿 사용 시 부팅 실패(인증 우회 방지)
    init {
        require(properties.secret.toByteArray().size >= MIN_SECRET_BYTES) {
            "JWT_SECRET은 최소 ${MIN_SECRET_BYTES}바이트 이상이어야 합니다(HS256)"
        }
        require(activeProfile in LOCAL_PROFILES || properties.secret != DEV_DEFAULT_SECRET) {
            "운영 환경(profile=$activeProfile)에서 기본 JWT 시크릿을 사용할 수 없습니다. JWT_SECRET 환경변수를 설정하세요."
        }
    }

    private val key: SecretKey = Keys.hmacShaKeyFor(properties.secret.toByteArray())

    fun createAccessToken(
        userId: Long,
        email: String,
    ): String {
        val now = Instant.now()
        return Jwts
            .builder()
            .subject(userId.toString())
            .claim("email", email)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(properties.accessTtlSeconds)))
            .signWith(key)
            .compact()
    }

    /**
     * 가입 대기(registerToken) 발급. 5분 TTL, type=REGISTER.
     * 비밀번호는 담지 않는다(소셜 전용 — 존재하지 않음). 절대 로깅하지 않는다.
     */
    fun generateRegisterToken(
        provider: String,
        providerId: String,
        email: String?,
        nickname: String?,
    ): String {
        val now = Instant.now()
        return Jwts
            .builder()
            .claim(CLAIM_TYPE, REGISTER_TYPE)
            .claim(CLAIM_PROVIDER, provider)
            .claim(CLAIM_PROVIDER_ID, providerId)
            .claim(CLAIM_EMAIL, email)
            .claim(CLAIM_NICKNAME, nickname)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(REGISTER_TTL_SECONDS)))
            .signWith(key)
            .compact()
    }

    /**
     * registerToken 검증 후 가입 대기 신원 반환. 서명·만료 위반 또는 type!=REGISTER이면 null.
     * (실패 응답은 호출부에서 INVALID_TOKEN(401)로 변환한다.)
     */
    fun parseRegisterToken(token: String): RegisterPrincipal? =
        try {
            val claims =
                Jwts
                    .parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .payload
            if (claims[CLAIM_TYPE] != REGISTER_TYPE) {
                null
            } else {
                val provider = claims[CLAIM_PROVIDER] as? String
                val providerId = claims[CLAIM_PROVIDER_ID] as? String
                if (provider.isNullOrBlank() || providerId.isNullOrBlank()) {
                    null
                } else {
                    RegisterPrincipal(
                        provider = provider,
                        providerId = providerId,
                        email = claims[CLAIM_EMAIL] as? String,
                        nickname = claims[CLAIM_NICKNAME] as? String,
                    )
                }
            }
        } catch (_: JwtException) {
            null
        }

    /** 서명/만료 검증 후 주체 반환. 실패 시 null(필터에서 401 처리). */
    fun parse(token: String): UserPrincipal? =
        try {
            val claims =
                Jwts
                    .parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .payload
            UserPrincipal(
                userId = claims.subject.toLong(),
                email = claims["email"] as? String ?: "",
            )
        } catch (_: JwtException) {
            null
        } catch (_: NumberFormatException) {
            null
        }

    private companion object {
        const val MIN_SECRET_BYTES = 32
        const val DEV_DEFAULT_SECRET = "local-dev-insecure-jwt-secret-please-change-32bytes+"
        val LOCAL_PROFILES = setOf("local", "test", "default")

        // registerToken claim 형태(SSOT). 5분 TTL — 온보딩 화면 체류 시간을 넉넉히 커버하되 탈취 노출은 최소화.
        const val REGISTER_TTL_SECONDS = 300L
        const val CLAIM_TYPE = "type"
        const val REGISTER_TYPE = "REGISTER"
        const val CLAIM_PROVIDER = "provider"
        const val CLAIM_PROVIDER_ID = "providerId"
        const val CLAIM_EMAIL = "email"
        const val CLAIM_NICKNAME = "nickname"
    }
}

/**
 * registerToken에서 추출한 가입 대기 신원. User는 아직 생성되지 않았다.
 * - [provider]/[providerId] 소셜 신원(필수). User 생성 키.
 * - [email]/[nickname] 소셜이 준 기본값(웹 프리필용). 사용자가 온보딩에서 수정 가능.
 */
data class RegisterPrincipal(
    val provider: String,
    val providerId: String,
    val email: String?,
    val nickname: String?,
)
