package kr.ai.flori.ai.service

import com.fasterxml.jackson.databind.ObjectMapper
import kr.ai.flori.ai.client.AiMessage
import kr.ai.flori.ai.client.AiServerClient
import kr.ai.flori.ai.config.AiGatewayProperties
import kr.ai.flori.ai.dto.AiSuggestion
import kr.ai.flori.ai.dto.ChatRequest
import kr.ai.flori.ai.dto.ChatResponse
import kr.ai.flori.ai.dto.ConfirmRequest
import kr.ai.flori.ai.dto.ConfirmResponse
import kr.ai.flori.ai.dto.ConfirmationCardResponse
import kr.ai.flori.ai.dto.ConfirmationField
import kr.ai.flori.ai.dto.OcrReservationRequest
import kr.ai.flori.ai.dto.ProactiveResponse
import kr.ai.flori.ai.entity.AiChatMessage
import kr.ai.flori.ai.entity.AiChatSession
import kr.ai.flori.ai.entity.AiProactiveLog
import kr.ai.flori.ai.entity.AiWriteProposal
import kr.ai.flori.ai.repository.AiChatMessageRepository
import kr.ai.flori.ai.repository.AiChatSessionRepository
import kr.ai.flori.ai.repository.AiProactiveLogRepository
import kr.ai.flori.ai.repository.AiWriteProposalRepository
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.reservations.dto.ReservationCreateRequest
import kr.ai.flori.reservations.service.ReservationService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * AI 게이트웨이 서비스. web ↔ ai-server 중개 + 모든 호출을 DB에 로깅한다.
 * 멀티테넌시: 모든 작업 TenantContext userId 격리. 쓰기(예약)는 confirm 경유만(human-in-loop).
 * ai-server는 stateless — 대화 히스토리/세션/제안은 여기 DB가 소유한다.
 */
@Service
class AiService(
    private val aiClient: AiServerClient,
    private val sessionRepository: AiChatSessionRepository,
    private val messageRepository: AiChatMessageRepository,
    private val proposalRepository: AiWriteProposalRepository,
    private val proactiveLogRepository: AiProactiveLogRepository,
    private val reservationService: ReservationService,
    private val properties: AiGatewayProperties,
    private val objectMapper: ObjectMapper,
    private val usageGuard: AiUsageGuard,
) {
    // @Transactional 미적용: aiClient 호출(최대 수십 초)이 DB 커넥션을 점유하지 않게 한다(풀 고갈 방지).
    // 각 저장은 독립 커밋 — 부분 실패 시에도 ai-server가 폴백 응답으로 200을 주므로 보통 양쪽이 기록된다.
    fun chat(
        userJwt: String,
        request: ChatRequest,
    ): ChatResponse {
        val userId = TenantContext.currentUserId()
        // 사전 차단(비원자적): 세션 생성·외부호출 전 빠른 거부. 최종 캡 강제는 admitChatMessage가 원자적으로 한다.
        if (usageGuard.isOverCap(userId)) throw AppException(CommonErrorCode.FORBIDDEN, CAP_MESSAGE)
        val message = request.message.orEmpty().trim()

        val session = resolveSession(userId, request.sessionToken, FEATURE_CHAT)
        // 캡 강제 + 유저 메시지 기록을 advisory lock 안에서 원자적으로(TOCTOU 방지).
        usageGuard.admitChatMessage(userId, session.id!!, message)

        val history =
            messageRepository
                .findBySessionIdAndUserIdOrderByCreatedAtAsc(session.id!!, userId)
                .takeLast(MAX_HISTORY) // 컨텍스트 폭발/비용 방어 — 최근 N턴만(마지막 = 방금 저장한 유저 발화)
                .map { AiMessage(it.role, it.content) }
        val start = System.nanoTime()
        val result = aiClient.chat(userJwt, userId, history)
        val latencyMs = elapsedMs(start)

        messageRepository.save(
            AiChatMessage(session.id!!, userId, ROLE_ASSISTANT, result.reply).apply {
                model = result.model
                inputTokens = result.inputTokens
                outputTokens = result.outputTokens
                this.latencyMs = latencyMs
            },
        )

        val now = Instant.now()
        if (session.firstMessageAt == null) session.firstMessageAt = now
        session.lastMessageAt = now
        if (session.title == null) session.title = message.take(TITLE_MAX)
        sessionRepository.save(session)

        return ChatResponse(reply = result.reply, sessionToken = session.sessionToken)
    }

    /** best-effort(fail-open): ai-server 장애 시 빈 제안을 반환해 대시보드가 깨지지 않게 한다. */
    @Suppress("SwallowedException", "TooGenericExceptionCaught") // 의도적 fail-open — 어떤 실패든 삼키고 빈 제안
    fun proactive(userJwt: String): ProactiveResponse {
        val userId = TenantContext.currentUserId()
        return try {
            val start = System.nanoTime()
            val result = aiClient.proactive(userJwt, userId)
            proactiveLogRepository.save(
                AiProactiveLog(userId).apply {
                    suggestionsJson = objectMapper.valueToTree(result.suggestions)
                    suggestionCount = result.suggestions.size
                    model = result.model
                    inputTokens = result.inputTokens
                    outputTokens = result.outputTokens
                    latencyMs = elapsedMs(start)
                },
            )
            ProactiveResponse(result.suggestions.map { AiSuggestion(it.title, it.detail) })
        } catch (e: Exception) {
            ProactiveResponse(emptyList())
        }
    }

    // @Transactional 미적용: ocrExtract(vision LLM, 수 초~수십 초)가 DB 커넥션을 점유하지 않게 한다.
    fun proposeOcrReservation(
        userJwt: String,
        request: OcrReservationRequest,
    ): ConfirmationCardResponse {
        val userId = TenantContext.currentUserId()
        // OCR은 ai_chat_message를 기록하지 않아 카운트를 증가시키지 않음 → 캡은 best-effort(사전 차단만).
        // 완전 원자화는 별도 usage 카운터 테이블이 필요(스키마 변경, 추후 결정).
        if (usageGuard.isOverCap(userId)) throw AppException(CommonErrorCode.FORBIDDEN, CAP_MESSAGE)
        val start = System.nanoTime()
        val result = aiClient.ocrExtract(userJwt, userId, request.imageUrl!!)
        val draft = result.draft
        if (draft.customerName.isNullOrBlank() || draft.date.isNullOrBlank() || draft.title.isNullOrBlank()) {
            throw AppException(CommonErrorCode.VALIDATION, "이미지에서 예약 정보를 충분히 읽지 못했어요.")
        }

        val proposalId = UUID.randomUUID().toString().replace("-", "")
        val payload =
            objectMapper.createObjectNode().apply {
                put("date", draft.date)
                draft.time?.let { put("time", it) }
                put("customerName", draft.customerName)
                draft.customerPhone?.let { put("customerPhone", it) }
                put("title", draft.title)
                put("amount", draft.amount ?: 0)
            }
        val expiresAt = Instant.now().plusSeconds(properties.proposalTtlSeconds)
        proposalRepository.save(
            AiWriteProposal(proposalId, userId, ACTION_CREATE_RESERVATION).apply {
                payloadJson = payload
                model = result.model
                inputTokens = result.inputTokens
                outputTokens = result.outputTokens
                latencyMs = elapsedMs(start)
                this.expiresAt = expiresAt
            },
        )

        val whenLabel = if (!draft.time.isNullOrBlank()) "${draft.date} ${draft.time}" else draft.date!!
        val fields =
            buildList {
                add(ConfirmationField("고객", draft.customerName!!))
                draft.customerPhone?.let { add(ConfirmationField("연락처", it)) }
                add(ConfirmationField("날짜", draft.date!!))
                draft.time?.let { add(ConfirmationField("시간", it)) }
                add(ConfirmationField("품목", draft.title!!))
                add(ConfirmationField("금액", "%,d원".format(draft.amount ?: 0)))
            }
        return ConfirmationCardResponse(
            proposalId = proposalId,
            action = ACTION_CREATE_RESERVATION,
            summary = "$whenLabel · ${draft.customerName} · ${draft.title}",
            fields = fields,
            expiresAt = expiresAt,
        )
    }

    /**
     * 확인 카드 실행 — 게이트웨이가 직접 예약을 생성한다(쓰기 종착점).
     * 만료 상태 갱신(ensureConfirmable)이 AppException throw로 롤백되지 않도록 noRollbackFor 지정.
     */
    @Transactional(noRollbackFor = [AppException::class])
    fun confirm(request: ConfirmRequest): ConfirmResponse {
        val userId = TenantContext.currentUserId()
        val proposal =
            proposalRepository.findByProposalIdAndUserId(request.proposalId!!, userId)
                ?: throw AppException(CommonErrorCode.NOT_FOUND, "제안을 찾을 수 없거나 만료되었어요.")
        ensureConfirmable(proposal)

        val createRequest = objectMapper.treeToValue(proposal.payloadJson, ReservationCreateRequest::class.java)
        val reservation = reservationService.create(createRequest)

        proposal.status = STATUS_CONFIRMED
        proposal.confirmedAt = Instant.now()
        proposal.resultId = reservation.id
        proposalRepository.save(proposal)

        return ConfirmResponse(action = proposal.action, reservationId = reservation.id)
    }

    /** 제안이 확인 가능한 상태인지 검증한다(만료면 상태 갱신 후 거부). */
    private fun ensureConfirmable(proposal: AiWriteProposal) {
        if (proposal.status != STATUS_PENDING) {
            throw AppException(CommonErrorCode.CONFLICT, "이미 처리된 제안이에요.")
        }
        if (proposal.expiresAt?.isBefore(Instant.now()) == true) {
            proposal.status = STATUS_EXPIRED
            proposalRepository.save(proposal)
            throw AppException(CommonErrorCode.NOT_FOUND, "제안이 만료되었어요.")
        }
    }

    private fun resolveSession(
        userId: Long,
        token: String?,
        feature: String,
    ): AiChatSession {
        if (!token.isNullOrBlank()) {
            sessionRepository.findByUserIdAndSessionTokenAndDeletedAtIsNull(userId, token)?.let { return it }
        }
        return sessionRepository.save(AiChatSession(userId, UUID.randomUUID().toString().replace("-", ""), feature))
    }

    private fun elapsedMs(start: Long): Int = ((System.nanoTime() - start) / NANOS_PER_MS).toInt()

    private companion object {
        const val FEATURE_CHAT = "chat"
        const val ROLE_ASSISTANT = "assistant"
        const val ACTION_CREATE_RESERVATION = "create_reservation"
        const val STATUS_PENDING = "pending"
        const val STATUS_CONFIRMED = "confirmed"
        const val STATUS_EXPIRED = "expired"
        const val TITLE_MAX = 40
        const val MAX_HISTORY = 30
        const val NANOS_PER_MS = 1_000_000L
        const val CAP_MESSAGE = "오늘 AI 사용량을 모두 사용했어요. 내일 다시 이용해 주세요."
    }
}
