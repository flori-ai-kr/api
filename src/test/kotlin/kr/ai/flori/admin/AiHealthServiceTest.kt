package kr.ai.flori.admin

import kr.ai.flori.admin.config.AiHealthProperties
import kr.ai.flori.admin.service.AiHealthService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AiHealthServiceTest {
    @Test
    fun `타깃 미설정이면 빈 결과`() {
        val service = AiHealthService(AiHealthProperties())
        assertTrue(service.check().targets.isEmpty())
    }

    @Test
    fun `도달 불가 host 는 DOWN 으로 degrade`() {
        val service = AiHealthService(AiHealthProperties(serverUrl = "http://127.0.0.1:1/health"))
        val target = service.check().targets.single()
        assertEquals("DOWN", target.status)
    }
}
