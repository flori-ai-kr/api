package com.hazel.common.error

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DiscordReportingTest {
    @Test
    fun `스택의 경로·이메일·시크릿을 마스킹한다`() {
        val raw =
            """
            at /Users/alice/project/App.kt:10
            user=bob@example.com token=abcd1234efgh password=hunter2
            """.trimIndent()

        val sanitized = sanitizeStack(raw)

        assertThat(sanitized).doesNotContain("/Users/alice")
        assertThat(sanitized).contains("/home/user")
        assertThat(sanitized).doesNotContain("bob@example.com")
        assertThat(sanitized).contains("[EMAIL]")
        assertThat(sanitized).contains("token=[REDACTED]")
        assertThat(sanitized).contains("password=[REDACTED]")
    }

    @Test
    fun `스택 줄 수를 제한한다`() {
        val many = (1..100).joinToString("\n") { "line $it" }
        assertThat(sanitizeStack(many).lines()).hasSize(MAX_STACK_LINES)
    }

    @Test
    fun `긴 문자열은 말줄임으로 잘린다`() {
        val long = "a".repeat(500)
        val result = truncate(long, MAX_FIELD_LENGTH)
        assertThat(result).hasSize(MAX_FIELD_LENGTH)
        assertThat(result).endsWith("...")
    }

    @Test
    fun `짧은 문자열은 그대로 둔다`() {
        assertThat(truncate("ok", MAX_FIELD_LENGTH)).isEqualTo("ok")
    }
}
