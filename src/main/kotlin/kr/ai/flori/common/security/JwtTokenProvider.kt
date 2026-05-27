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
    }
}
