package kr.ai.flori.ai.docs

import kr.ai.flori.ai.client.AiChatResult
import kr.ai.flori.ai.client.AiDraft
import kr.ai.flori.ai.client.AiOcrResult
import kr.ai.flori.ai.client.AiProactiveResult
import kr.ai.flori.ai.client.AiServerClient
import kr.ai.flori.ai.client.AiServerSuggestion
import kr.ai.flori.ai.entity.AiWriteProposal
import kr.ai.flori.ai.repository.AiWriteProposalRepository
import kr.ai.flori.common.docs.RestDocsSupport
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.util.UUID

/**
 * AI 게이트웨이(/ai 하위 전체) RestDocs 문서화. ai-server HTTP 호출([AiServerClient])만 스텁하고,
 * 세션/메시지/제안 영속과 confirm 쓰기(예약 생성)는 실제 Zonky PG에서 수행한다.
 */
class AiDocsTest : RestDocsSupport() {
    @MockitoBean
    private lateinit var aiClient: AiServerClient

    @Autowired
    private lateinit var proposalRepository: AiWriteProposalRepository

    @Test
    fun `AI 채팅 문서화`() {
        val token = signupAndToken()
        Mockito
            .`when`(aiClient.chat(anyString(), anyLong(), anyList()))
            .thenReturn(AiChatResult(reply = "장미 입고는 보통 수요일이에요."))

        mockMvc
            .post("/ai/chat") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("message" to "장미 언제 들어와요?"))
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "ai-chat",
                        requestSchema = "ChatRequest",
                        responseSchema = "ChatResponse",
                        tag = "AI",
                        summary = "AI 채팅(세션 누적). sessionToken 생략 시 새 세션 시작",
                        requestFields =
                            listOf(
                                fieldWithPath("message").type(JsonFieldType.STRING).description("사용자 메시지(필수, 최대 4000자)"),
                                fieldWithPath("sessionToken").type(JsonFieldType.STRING).optional().description("이어갈 세션 토큰(없으면 새 세션)"),
                            ),
                        responseFields =
                            listOf(
                                fieldWithPath("reply").type(JsonFieldType.STRING).description("AI 응답"),
                                fieldWithPath("sessionToken").type(JsonFieldType.STRING).description("세션 토큰(이어가기용)"),
                            ),
                    ),
                )
            }
    }

    @Test
    fun `AI 프로액티브 제안 문서화`() {
        val token = signupAndToken()
        Mockito
            .`when`(aiClient.proactive(anyString(), anyLong()))
            .thenReturn(
                AiProactiveResult(
                    suggestions =
                        listOf(
                            AiServerSuggestion("재고 점검", "장미가 부족해요"),
                            AiServerSuggestion("리마인더", "내일 픽업 2건"),
                        ),
                ),
            )

        mockMvc
            .get("/ai/proactive") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "ai-proactive",
                        responseSchema = "ProactiveResponse",
                        tag = "AI",
                        summary = "AI 선제 제안(대시보드용, fail-open)",
                        responseFields =
                            listOf(
                                fieldWithPath("suggestions").type(JsonFieldType.ARRAY).description("제안 목록(장애 시 빈 배열)"),
                                fieldWithPath("suggestions[].title").type(JsonFieldType.STRING).optional().description("제안 제목"),
                                fieldWithPath("suggestions[].detail").type(JsonFieldType.STRING).optional().description("제안 상세"),
                            ),
                    ),
                )
            }
    }

    @Test
    fun `OCR 예약 제안 문서화`() {
        val token = signupAndToken()
        Mockito
            .`when`(aiClient.ocrExtract(anyString(), anyLong(), anyString()))
            .thenReturn(
                AiOcrResult(
                    draft =
                        AiDraft(
                            customerName = "김하늘",
                            customerPhone = "010-1234-5678",
                            date = "2026-06-10",
                            time = "14:00",
                            title = "장미 꽃다발",
                            amount = 50_000,
                        ),
                ),
            )

        mockMvc
            .post("/ai/ocr/reservation") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("imageUrl" to "https://cdn.example.com/orders/a.jpg"))
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "ai-ocr-reservation",
                        requestSchema = "OcrReservationRequest",
                        responseSchema = "ConfirmationCardResponse",
                        tag = "AI",
                        summary = "주문서 이미지 OCR → 예약 확인 카드(쓰기는 /ai/confirm)",
                        requestFields =
                            listOf(
                                fieldWithPath("imageUrl").type(JsonFieldType.STRING).description("주문서 이미지 URL(필수)"),
                            ),
                        responseFields =
                            listOf(
                                fieldWithPath("proposalId").type(JsonFieldType.STRING).description("확인 시 사용할 제안 식별자"),
                                fieldWithPath("action").type(JsonFieldType.STRING).description("실행 액션(create_reservation)"),
                                fieldWithPath("summary").type(JsonFieldType.STRING).description("요약 문장"),
                                fieldWithPath("fields").type(JsonFieldType.ARRAY).description("확인 카드 항목"),
                                fieldWithPath("fields[].label").type(JsonFieldType.STRING).description("항목명"),
                                fieldWithPath("fields[].value").type(JsonFieldType.STRING).description("항목값"),
                                fieldWithPath("expiresAt").type(JsonFieldType.STRING).description("제안 만료 시각(ISO-8601)"),
                            ),
                    ),
                )
            }
    }

    @Test
    fun `확인 카드 실행(예약 생성) 문서화`() {
        val token = signupAndToken()
        val userId = tokenProvider.parse(token)!!.userId
        val proposalId = UUID.randomUUID().toString().replace("-", "")
        proposalRepository.save(
            AiWriteProposal(proposalId, userId, "create_reservation").apply {
                payloadJson =
                    objectMapper.createObjectNode().apply {
                        put("date", "2026-06-10")
                        put("time", "14:00")
                        put("customerName", "김하늘")
                        put("customerPhone", "010-1234-5678")
                        put("title", "장미 꽃다발")
                        put("amount", 50_000)
                    }
                expiresAt = Instant.now().plusSeconds(600)
            },
        )

        mockMvc
            .post("/ai/confirm") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("proposalId" to proposalId))
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "ai-confirm",
                        requestSchema = "ConfirmRequest",
                        responseSchema = "ConfirmResponse",
                        tag = "AI",
                        summary = "확인 카드 실행 — 게이트웨이가 예약을 직접 생성(human-in-loop 쓰기 종착점)",
                        requestFields =
                            listOf(
                                fieldWithPath("proposalId").type(JsonFieldType.STRING).description("OCR 제안 식별자"),
                            ),
                        responseFields =
                            listOf(
                                fieldWithPath("action").type(JsonFieldType.STRING).description("실행된 액션"),
                                fieldWithPath("reservationId").type(JsonFieldType.NUMBER).optional().description("생성된 예약 ID"),
                            ),
                    ),
                )
            }
    }
}
