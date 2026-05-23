package com.hazel.common.security

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
    private val provider = JwtTokenProvider(properties)

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
        val expiredProvider = JwtTokenProvider(properties.copy(accessTtlSeconds = -1))
        val token = expiredProvider.createAccessToken(UUID.randomUUID(), "a@b.com")

        assertThat(provider.parse(token)).isNull()
    }

    @Test
    fun `형식이 아닌 문자열은 null을 반환한다`() {
        assertThat(provider.parse("not-a-jwt")).isNull()
    }
}
