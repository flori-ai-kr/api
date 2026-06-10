package kr.ai.flori.interview.controller

import com.fasterxml.jackson.databind.ObjectMapper
import kr.ai.flori.common.error.DiscordErrorReporter
import kr.ai.flori.interview.dto.InterviewApplyRequest
import kr.ai.flori.interview.dto.InterviewApplyResponse
import kr.ai.flori.interview.service.InterviewService
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@WebMvcTest(InterviewController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(DiscordErrorReporter::class)
class InterviewControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @MockitoBean
    lateinit var service: InterviewService

    @Test
    fun `POST interview 는 201과 applied=true 를 반환한다`() {
        Mockito
            .`when`(
                service.apply(Mockito.any(InterviewApplyRequest::class.java) ?: InterviewApplyRequest("", "")),
            ).thenReturn(InterviewApplyResponse(applied = true))
        val body =
            objectMapper.writeValueAsString(
                InterviewApplyRequest(name = "홍길동", phone = "010-1234-5678"),
            )
        mockMvc
            .post("/interview") {
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect {
                status { isCreated() }
                jsonPath("$.applied") { value(true) }
            }
    }

    @Test
    fun `POST interview 전화번호 형식 오류 시 400`() {
        val body = objectMapper.writeValueAsString(InterviewApplyRequest(name = "홍길동", phone = "12345"))
        mockMvc
            .post("/interview") {
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `POST interview 이름 누락 시 400`() {
        val body = objectMapper.writeValueAsString(InterviewApplyRequest(name = "", phone = "010-1234-5678"))
        mockMvc
            .post("/interview") {
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect {
                status { isBadRequest() }
            }
    }
}
