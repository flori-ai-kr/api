package kr.ai.flori.ai.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

// ─── web ↔ 게이트웨이(Spring) 계약 ──────────────────────────────
// web은 Spring `/ai/*`만 호출한다. ai-server 존재를 모른다.

data class ChatRequest(
    @field:NotBlank(message = "메시지를 입력해 주세요")
    @field:Size(max = 4000, message = "메시지가 너무 깁니다")
    val message: String?,
    val sessionToken: String? = null,
)

data class ChatResponse(
    val reply: String,
    val sessionToken: String,
)

data class AiSuggestion(
    val title: String,
    val detail: String,
)

data class ProactiveResponse(
    val suggestions: List<AiSuggestion>,
)

data class OcrReservationRequest(
    @field:NotBlank(message = "이미지 URL이 필요합니다")
    @field:Size(max = 2000)
    val imageUrl: String?,
)

data class ConfirmationField(
    val label: String,
    val value: String,
)

/** OCR 추출 확인 카드. proposalId로 /ai/confirm 호출 시 실제 예약 생성. */
data class ConfirmationCardResponse(
    val proposalId: String,
    val action: String,
    val summary: String,
    val fields: List<ConfirmationField>,
    val expiresAt: Instant,
)

data class ConfirmRequest(
    @field:NotBlank(message = "제안 식별자가 필요합니다")
    val proposalId: String?,
)

data class ConfirmResponse(
    val action: String,
    val reservationId: Long?,
)
