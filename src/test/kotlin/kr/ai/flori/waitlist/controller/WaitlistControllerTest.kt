package kr.ai.flori.waitlist.controller

import com.fasterxml.jackson.databind.ObjectMapper
import kr.ai.flori.common.error.DiscordErrorReporter
import kr.ai.flori.waitlist.dto.WaitlistCountResponse
import kr.ai.flori.waitlist.dto.WaitlistRegisterRequest
import kr.ai.flori.waitlist.dto.WaitlistRegisterResponse
import kr.ai.flori.waitlist.service.WaitlistService
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@WebMvcTest(WaitlistController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(DiscordErrorReporter::class)
class WaitlistControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @MockitoBean
    lateinit var service: WaitlistService

    @Test
    fun `POST waitlist 는 201과 카운트를 반환한다`() {
        Mockito
            .`when`(
                service.register(Mockito.any(WaitlistRegisterRequest::class.java) ?: WaitlistRegisterRequest("", "")),
            ).thenReturn(WaitlistRegisterResponse(1, 100, false))
        val body =
            objectMapper.writeValueAsString(
                WaitlistRegisterRequest(email = "hazel@flori.ai.kr", shopName = "헤이즐"),
            )
        mockMvc
            .post("/waitlist") {
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect {
                status { isCreated() }
                jsonPath("$.count") { value(1) }
                jsonPath("$.capacity") { value(100) }
            }
    }

    @Test
    fun `GET waitlist count 는 카운트를 반환한다`() {
        Mockito.`when`(service.count()).thenReturn(WaitlistCountResponse(24, 100, false))
        mockMvc.get("/waitlist/count").andExpect {
            status { isOk() }
            jsonPath("$.count") { value(24) }
            jsonPath("$.closed") { value(false) }
        }
    }

    @Test
    fun `POST waitlist 이메일 형식 오류 시 400`() {
        val body = objectMapper.writeValueAsString(WaitlistRegisterRequest(email = "not-an-email", shopName = "헤이즐"))
        mockMvc
            .post("/waitlist") {
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect {
                status { isBadRequest() }
            }
    }
}
