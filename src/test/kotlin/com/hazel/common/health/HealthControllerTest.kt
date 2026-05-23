package com.hazel.common.health

import com.hazel.common.error.DiscordErrorReporter
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(HealthController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(DiscordErrorReporter::class)
class HealthControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `health 엔드포인트는 UP 상태를 반환한다`() {
        mockMvc.get("/health").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("UP") }
            jsonPath("$.service") { value("hazel-server") }
        }
    }
}
