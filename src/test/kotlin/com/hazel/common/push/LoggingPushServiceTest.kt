package com.hazel.common.push

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LoggingPushServiceTest {
    private val service = LoggingPushService()

    @Test
    fun `폴백 푸시 서비스는 성공을 반환하며 예외를 던지지 않는다`() {
        val result = service.send(PushMessage(token = "device-token", title = "제목", body = "본문"))
        assertThat(result.success).isTrue()
        assertThat(result.tokenInvalid).isFalse()
    }
}
