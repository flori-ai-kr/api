package com.hazel.common.security

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

/**
 * access 토큰(자체 JWT, HS256) 발급/검증. refresh 토큰은 불투명 난수로 별도 관리(AuthService).
 */
@Component
class JwtTokenProvider(
    private val properties: JwtProperties,
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(properties.secret.toByteArray())

    fun createAccessToken(
        userId: UUID,
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
                userId = UUID.fromString(claims.subject),
                email = claims["email"] as? String ?: "",
            )
        } catch (_: JwtException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
}
