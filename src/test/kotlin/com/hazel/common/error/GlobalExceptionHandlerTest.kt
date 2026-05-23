package com.hazel.common.error

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * @ControllerAdvice 표준 응답 매핑 + 예기치 못한 오류의 Discord 리포팅을 standalone MockMvc로 검증.
 */
class GlobalExceptionHandlerTest {
    private val reporter = RecordingReporter()
    private val mockMvc =
        MockMvcBuilders
            .standaloneSetup(DummyController())
            .setControllerAdvice(GlobalExceptionHandler(reporter))
            .build()

    @Test
    fun `AppException은 해당 상태코드와 코드로 매핑된다`() {
        mockMvc.get("/dummy/app").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("NOT_FOUND") }
        }
        assertThat(reporter.reported).isNull()
    }

    @Test
    fun `데이터 제약 위반은 409로 매핑된다`() {
        mockMvc.get("/dummy/conflict").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("DUPLICATE") }
        }
        assertThat(reporter.reported).isNull()
    }

    @Test
    fun `예기치 못한 예외는 500 일반메시지 + Discord 리포팅이며 내부 디테일을 노출하지 않는다`() {
        val response =
            mockMvc
                .get("/dummy/boom")
                .andExpect {
                    status { isInternalServerError() }
                    jsonPath("$.code") { value("INTERNAL") }
                }.andReturn()
                .response.contentAsString

        assertThat(reporter.reported).isNotNull
        assertThat(response).doesNotContain("token=secret123")
    }

    @RestController
    class DummyController {
        @GetMapping("/dummy/app")
        fun app(): Nothing = throw AppException(ErrorCode.NOT_FOUND)

        @GetMapping("/dummy/conflict")
        fun conflict(): Nothing = throw DataIntegrityViolationException("dup")

        @GetMapping("/dummy/boom")
        fun boom(): Nothing = throw IllegalStateException("db lost token=secret123")
    }

    class RecordingReporter : DiscordErrorReporter("") {
        var reported: Throwable? = null

        override fun report(
            throwable: Throwable,
            context: Map<String, String>,
        ) {
            reported = throwable
        }
    }
}
