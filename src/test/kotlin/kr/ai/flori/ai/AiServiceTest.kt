package kr.ai.flori.ai

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.ai.client.AiChatResult
import kr.ai.flori.ai.client.AiDraft
import kr.ai.flori.ai.client.AiOcrResult
import kr.ai.flori.ai.client.AiProactiveResult
import kr.ai.flori.ai.client.AiServerClient
import kr.ai.flori.ai.client.AiServerSuggestion
import kr.ai.flori.ai.dto.ChatRequest
import kr.ai.flori.ai.dto.ConfirmRequest
import kr.ai.flori.ai.dto.OcrReservationRequest
import kr.ai.flori.ai.entity.AiChatMessage
import kr.ai.flori.ai.entity.AiChatSession
import kr.ai.flori.ai.entity.AiWriteProposal
import kr.ai.flori.ai.repository.AiChatMessageRepository
import kr.ai.flori.ai.repository.AiChatSessionRepository
import kr.ai.flori.ai.repository.AiProactiveLogRepository
import kr.ai.flori.ai.repository.AiWriteProposalRepository
import kr.ai.flori.ai.service.AiService
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.reservations.repository.ReservationRepository
import kr.ai.flori.support.TestAccounts
import kr.ai.flori.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Instant
import java.util.UUID

/**
 * AI 게이트웨이 서비스 통합테스트. ai-server HTTP 호출([AiServerClient])만 [MockitoBean]으로 스텁하고,
 * 세션/메시지/제안 영속과 멀티테넌시는 실제 Zonky PG에서 검증한다.
 *
 * 일일 캡은 5로 낮춰(아래 [TestPropertySource]) 캡 초과 경로를 적은 시드로 재현한다.
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
@TestPropertySource(properties = ["ai.gateway.usage-cap-per-day=5"])
class AiServiceTest {
    @Autowired
    lateinit var aiService: AiService

    @MockitoBean
    lateinit var aiClient: AiServerClient

    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var tokenProvider: JwtTokenProvider

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var sessionRepository: AiChatSessionRepository

    @Autowired
    lateinit var messageRepository: AiChatMessageRepository

    @Autowired
    lateinit var proposalRepository: AiWriteProposalRepository

    @Autowired
    lateinit var proactiveLogRepository: AiProactiveLogRepository

    @Autowired
    lateinit var reservationRepository: ReservationRepository

    @AfterEach
    fun tearDown() = TenantContext.clear()

    private fun newTenant(): Long {
        val email = "ai-${UUID.randomUUID()}@flori.dev"
        TestAccounts.register(authService, tokenProvider, email)
        val userId = requireNotNull(userRepository.findByEmail(email)).id!!
        TenantContext.set(userId)
        return userId
    }

    @Test
    fun `chat - 유저·어시스턴트 메시지를 저장하고 세션 토큰과 답변을 반환한다`() {
        val userId = newTenant()
        Mockito
            .`when`(aiClient.chat(anyString(), anyLong(), anyList()))
            .thenReturn(AiChatResult(reply = "안녕하세요 사장님!", model = "claude-sonnet-4-6", inputTokens = 10, outputTokens = 20))

        val response = aiService.chat("jwt", ChatRequest(message = "오늘 매출 어때?"))

        assertThat(response.reply).isEqualTo("안녕하세요 사장님!")
        assertThat(response.sessionToken).isNotBlank()

        val session = sessionRepository.findByUserIdAndSessionTokenAndDeletedAtIsNull(userId, response.sessionToken)
        assertThat(session).isNotNull
        // 유저 발화 + 어시스턴트 응답 2건 저장, 세션 제목은 첫 메시지로 설정
        val messages = messageRepository.findBySessionIdAndUserIdOrderByCreatedAtAsc(session!!.id!!, userId)
        assertThat(messages.map { it.role }).containsExactly("user", "assistant")
        // 어시스턴트 메시지에 ai-server 응답 메타데이터(모델·토큰)가 적재된다
        assertThat(messages[1].model).isEqualTo("claude-sonnet-4-6")
        assertThat(messages[1].inputTokens).isEqualTo(10)
        assertThat(messages[1].outputTokens).isEqualTo(20)
        assertThat(session.title).isEqualTo("오늘 매출 어때?")
    }

    @Test
    fun `chat - 같은 세션 토큰으로 이어가면 같은 세션에 누적된다`() {
        val userId = newTenant()
        Mockito
            .`when`(aiClient.chat(anyString(), anyLong(), anyList()))
            .thenReturn(AiChatResult(reply = "네"))

        val first = aiService.chat("jwt", ChatRequest(message = "첫 질문"))
        val second = aiService.chat("jwt", ChatRequest(message = "두 번째", sessionToken = first.sessionToken))

        assertThat(second.sessionToken).isEqualTo(first.sessionToken)
        val session = sessionRepository.findByUserIdAndSessionTokenAndDeletedAtIsNull(userId, first.sessionToken)!!
        // 두 번의 대화 = 유저 2 + 어시스턴트 2 = 4건
        assertThat(messageRepository.findBySessionIdAndUserIdOrderByCreatedAtAsc(session.id!!, userId)).hasSize(4)
    }

    @Test
    fun `chat - 일일 캡을 초과하면 403`() {
        val userId = newTenant()
        // 캡(5)만큼 메시지를 미리 채운다 → 다음 호출은 거부되어야 한다
        val session = sessionRepository.save(AiChatSession(userId, UUID.randomUUID().toString().replace("-", ""), "chat"))
        repeat(5) { messageRepository.save(AiChatMessage(session.id!!, userId, "user", "기존 메시지 $it")) }
        // 전제조건 고정: createdAt(@CreatedDate)이 채워져 카운트 대상이 됨을 확인 (미충족이면 캡 경로가 false-green)
        assertThat(messageRepository.countByUserIdAndCreatedAtAfter(userId, Instant.now().minusSeconds(3600))).isEqualTo(5)

        assertThatThrownBy { aiService.chat("jwt", ChatRequest(message = "한도 초과 질문")) }
            .isInstanceOfSatisfying(AppException::class.java) {
                assertThat(it.errorCode).isEqualTo(CommonErrorCode.FORBIDDEN)
            }
        // 캡 초과 시 ai-server 호출 자체가 차단되어야 한다(비용/한도 우회 방지)
        Mockito.verify(aiClient, Mockito.never()).chat(anyString(), anyLong(), anyList())
    }

    @Test
    fun `proactive - 제안을 매핑해 반환하고 로그를 남긴다`() {
        val userId = newTenant()
        Mockito
            .`when`(aiClient.proactive(anyString(), anyLong()))
            .thenReturn(
                AiProactiveResult(
                    suggestions = listOf(AiServerSuggestion("재고 점검", "장미가 부족해요"), AiServerSuggestion("리마인더", "내일 픽업 2건")),
                    model = "claude-sonnet-4-6",
                ),
            )

        val response = aiService.proactive("jwt")

        assertThat(response.suggestions).hasSize(2)
        assertThat(response.suggestions.first().title).isEqualTo("재고 점검")
        assertThat(proactiveLogRepository.findAll().filter { it.userId == userId }).hasSize(1)
    }

    @Test
    fun `proactive - ai-server 장애 시 빈 제안으로 fail-open 한다`() {
        newTenant()
        Mockito
            .`when`(aiClient.proactive(anyString(), anyLong()))
            .thenThrow(RuntimeException("ai-server down"))

        assertThat(aiService.proactive("jwt").suggestions).isEmpty()
    }

    @Test
    fun `OCR 제안 - 초안이 불완전하면 검증 에러`() {
        newTenant()
        Mockito
            .`when`(aiClient.ocrExtract(anyString(), anyLong(), anyString()))
            .thenReturn(AiOcrResult(draft = AiDraft(customerName = null, date = "2026-06-10", title = "꽃다발")))

        assertThatThrownBy { aiService.proposeOcrReservation("jwt", OcrReservationRequest(imageUrl = "https://img/1.jpg")) }
            .isInstanceOfSatisfying(AppException::class.java) {
                assertThat(it.errorCode).isEqualTo(CommonErrorCode.VALIDATION)
            }
    }

    @Test
    fun `OCR 제안 - 완전한 초안이면 확인 카드와 제안을 저장한다`() {
        val userId = newTenant()
        Mockito
            .`when`(aiClient.ocrExtract(anyString(), anyLong(), anyString()))
            .thenReturn(
                AiOcrResult(
                    draft =
                        AiDraft(
                            customerName = "김플로리",
                            customerPhone = "010-1234-5678",
                            date = "2026-06-10",
                            time = "14:30",
                            title = "졸업식 꽃다발",
                            amount = 50000,
                        ),
                ),
            )

        val card = aiService.proposeOcrReservation("jwt", OcrReservationRequest(imageUrl = "https://img/1.jpg"))

        assertThat(card.proposalId).isNotBlank()
        assertThat(card.action).isEqualTo("create_reservation")
        assertThat(card.summary).contains("김플로리").contains("졸업식 꽃다발")
        assertThat(card.fields.map { it.label }).contains("고객", "날짜", "품목", "금액")
        val proposal = proposalRepository.findByProposalIdAndUserId(card.proposalId, userId)
        assertThat(proposal).isNotNull
        assertThat(proposal!!.status).isEqualTo("pending")
    }

    @Test
    fun `confirm - 제안을 예약으로 전환하고 상태를 confirmed로 만든다`() {
        val userId = newTenant()
        Mockito
            .`when`(aiClient.ocrExtract(anyString(), anyLong(), anyString()))
            .thenReturn(
                AiOcrResult(
                    draft =
                        AiDraft(
                            customerName = "이고객",
                            date = "2026-06-12",
                            time = "11:00",
                            title = "개업화환",
                            amount = 80000,
                        ),
                ),
            )
        val card = aiService.proposeOcrReservation("jwt", OcrReservationRequest(imageUrl = "https://img/2.jpg"))

        val result = aiService.confirm(ConfirmRequest(proposalId = card.proposalId))

        assertThat(result.action).isEqualTo("create_reservation")
        assertThat(result.reservationId).isNotNull
        assertThat(reservationRepository.findById(result.reservationId!!)).isPresent
        val proposal = proposalRepository.findByProposalIdAndUserId(card.proposalId, userId)!!
        assertThat(proposal.status).isEqualTo("confirmed")
        assertThat(proposal.resultId).isEqualTo(result.reservationId)
    }

    @Test
    fun `confirm - 존재하지 않는 제안은 404`() {
        newTenant()
        assertThatThrownBy { aiService.confirm(ConfirmRequest(proposalId = "nonexistent")) }
            .isInstanceOfSatisfying(AppException::class.java) {
                assertThat(it.errorCode).isEqualTo(CommonErrorCode.NOT_FOUND)
            }
    }

    @Test
    fun `confirm - 이미 처리된 제안은 409`() {
        newTenant()
        Mockito
            .`when`(aiClient.ocrExtract(anyString(), anyLong(), anyString()))
            .thenReturn(
                AiOcrResult(draft = AiDraft(customerName = "중복고객", date = "2026-06-13", title = "꽃바구니", amount = 30000)),
            )
        val card = aiService.proposeOcrReservation("jwt", OcrReservationRequest(imageUrl = "https://img/3.jpg"))
        aiService.confirm(ConfirmRequest(proposalId = card.proposalId)) // 1차 확인 성공

        assertThatThrownBy { aiService.confirm(ConfirmRequest(proposalId = card.proposalId)) }
            .isInstanceOfSatisfying(AppException::class.java) {
                assertThat(it.errorCode).isEqualTo(CommonErrorCode.CONFLICT)
            }
    }

    @Test
    fun `confirm - 만료된 제안은 404로 거부하고 상태를 expired로 갱신한다`() {
        val userId = newTenant()
        val proposal =
            AiWriteProposal(UUID.randomUUID().toString().replace("-", ""), userId, "create_reservation").apply {
                expiresAt = Instant.now().minusSeconds(60)
            }
        proposalRepository.save(proposal)

        assertThatThrownBy { aiService.confirm(ConfirmRequest(proposalId = proposal.proposalId)) }
            .isInstanceOfSatisfying(AppException::class.java) {
                assertThat(it.errorCode).isEqualTo(CommonErrorCode.NOT_FOUND)
            }
        assertThat(proposalRepository.findByProposalIdAndUserId(proposal.proposalId, userId)!!.status).isEqualTo("expired")
    }

    @Test
    fun `멀티테넌시 - 다른 테넌트의 제안은 confirm 할 수 없다`() {
        val owner = newTenant()
        Mockito
            .`when`(aiClient.ocrExtract(anyString(), anyLong(), anyString()))
            .thenReturn(
                AiOcrResult(draft = AiDraft(customerName = "소유자고객", date = "2026-06-14", title = "꽃다발", amount = 20000)),
            )
        val card = aiService.proposeOcrReservation("jwt", OcrReservationRequest(imageUrl = "https://img/4.jpg"))

        // 다른 사용자로 전환 후 같은 proposalId confirm 시도 → 본인 것이 아니므로 404
        newTenant()
        assertThatThrownBy { aiService.confirm(ConfirmRequest(proposalId = card.proposalId)) }
            .isInstanceOfSatisfying(AppException::class.java) {
                assertThat(it.errorCode).isEqualTo(CommonErrorCode.NOT_FOUND)
            }
        // 소유자 제안은 그대로 pending 유지
        TenantContext.set(owner)
        assertThat(proposalRepository.findByProposalIdAndUserId(card.proposalId, owner)!!.status).isEqualTo("pending")
    }
}
