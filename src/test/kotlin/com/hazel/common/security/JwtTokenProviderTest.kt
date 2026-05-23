package com.hazel.common.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class JwtTokenProviderTest {
    private val properties =
        JwtProperties(
            secret = "test-secret-test-secret-test-secret-1234567890",
            accessTtlSeconds = 900,
            refreshTtlSeconds = 1_209_600,
        )
    private val provider = JwtTokenProvider(properties, "test")

    @Test
    fun `발급한 토큰을 파싱하면 동일한 주체가 나온다`() {
        val userId = UUID.randomUUID()
        val token = provider.createAccessToken(userId, "a@b.com")

        val principal = provider.parse(token)

        assertThat(principal).isNotNull
        assertThat(principal!!.userId).isEqualTo(userId)
        assertThat(principal.email).isEqualTo("a@b.com")
    }

    @Test
    fun `위변조된 토큰은 null을 반환한다`() {
        val token = provider.createAccessToken(UUID.randomUUID(), "a@b.com")
        val tampered = token.dropLast(3) + "abc"

        assertThat(provider.parse(tampered)).isNull()
    }

    @Test
    fun `만료된 토큰은 null을 반환한다`() {
        val expiredProvider = JwtTokenProvider(properties.copy(accessTtlSeconds = -1), "test")
        val token = expiredProvider.createAccessToken(UUID.randomUUID(), "a@b.com")

        assertThat(provider.parse(token)).isNull()
    }

    @Test
    fun `형식이 아닌 문자열은 null을 반환한다`() {
        assertThat(provider.parse("not-a-jwt")).isNull()
    }

    @Test
    fun `alg=none 토큰은 거부된다`() {
        // 서명 없는(alg:none) 토큰 — 알고리즘 혼동 공격 방어
        val unsigned =
            Jwts
                .builder()
                .subject(UUID.randomUUID().toString())
                .claim("email", "x@y.com")
                .compact()
        assertThat(provider.parse(unsigned)).isNull()
    }

    @Test
    fun `다른 키로 서명된 토큰은 거부된다`() {
        val foreignKey = Keys.hmacShaKeyFor("another-secret-another-secret-1234567890".toByteArray())
        val forged =
            Jwts
                .builder()
                .subject(UUID.randomUUID().toString())
                .signWith(foreignKey)
                .compact()
        assertThat(provider.parse(forged)).isNull()
    }

    @Test
    fun `비-로컬 프로필에서 기본 시크릿은 부팅을 거부한다`() {
        val defaultProps = properties.copy(secret = "local-dev-insecure-jwt-secret-please-change-32bytes+")
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            JwtTokenProvider(defaultProps, "prod")
        }
    }
}
