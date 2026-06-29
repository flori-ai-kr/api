package kr.ai.flori.ai.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
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

// ─── 마케팅(블로그 초안) — web ↔ 게이트웨이 계약(camelCase) ───────────────────

/** 블로그 초안 생성 요청. 키워드만 필수. 사진은 0~4장(SSRF는 ai-server가, 개수/길이는 게이트웨이가 방어). */
data class BlogGenerateRequest(
    @field:NotBlank(message = "키워드를 입력해 주세요")
    @field:Size(max = 200, message = "키워드가 너무 깁니다")
    val keyword: String?,
    @field:Size(max = 100, message = "상황이 너무 깁니다")
    val situation: String? = null,
    @field:Size(max = 500, message = "메모가 너무 깁니다")
    val memo: String? = null,
    @field:Size(max = 4, message = "사진은 최대 4장까지 첨부할 수 있어요")
    val photoUrls: List<
        @Size(max = 2000, message = "사진 URL이 너무 깁니다")
        String,
    >? = null,
)

data class BlogSection(
    val heading: String,
    val body: String,
)

data class BlogFaq(
    val q: String,
    val a: String,
)

/** 생성된 블로그 초안. ai-server BlogDraft를 그대로 미러링한 게이트웨이 공개 계약. */
data class BlogDraft(
    val title: String,
    val sections: List<BlogSection>,
    val faq: List<BlogFaq>,
    val hashtags: List<String>,
)

/** 블로그 생성 응답. contentId로 상세/복사/소프트삭제(공개 계약은 문자열 ID — AI 계약 관행). */
data class BlogGenerateResponse(
    val contentId: String,
    val draft: BlogDraft,
)

// ─── 프롬프트 플레이그라운드(SPEC-AI-008) — 어드민 콘솔 즉석 미리보기(저장 안 함) ───

/** 편집 중인 프롬프트 초안(정적 부분). 저장 전 미리보기 생성에만 쓴다. */
data class BlogPromptDraft(
    val systemMd: String? = null,
    val rulesMd: String? = null,
    val outputSpecMd: String? = null,
    val model: String? = null,
    val temperature: BigDecimal? = null,
)

/** 미리보기용 샘플 입력(동적 데이터). 어드민이 콘솔에서 입력. */
data class BlogPreviewSample(
    @field:Size(max = 200)
    val keyword: String = "장미 꽃다발",
    @field:Size(max = 100)
    val situation: String? = null,
    @field:Size(max = 500)
    val memo: String? = null,
    @field:Size(max = 3)
    val toneSamples: List<
        @Size(max = 4000)
        String,
    > = emptyList(),
)

data class BlogPreviewRequest(
    val promptDraft: BlogPromptDraft = BlogPromptDraft(),
    val sampleInput: BlogPreviewSample = BlogPreviewSample(),
)

/** 미리보기 결과. 저장하지 않으므로 contentId가 없다(활성본·DB 영향 없음). */
data class BlogPreviewResponse(
    val draft: BlogDraft,
    val model: String,
)

/** 말투 프로필 조회/수정. 샘플은 0~3개. */
data class ToneProfileResponse(
    val samples: List<String>,
)

data class ToneProfileUpdateRequest(
    @field:Size(max = 3, message = "샘플은 최대 3개까지 등록할 수 있어요")
    val samples: List<
        @Size(max = 4000, message = "샘플이 너무 깁니다")
        String,
    >? = null,
)

/** 마케팅 콘텐츠 목록 항목(요약). 공개 계약은 문자열 ID. 본문(섹션/FAQ)은 상세에서. */
data class MarketingContentSummary(
    val id: String,
    val channel: String,
    val title: String,
    val keyword: String,
    val createdAt: Instant,
)

data class MarketingContentsResponse(
    val contents: List<MarketingContentSummary>,
    val hasMore: Boolean,
)

/** 마케팅 콘텐츠 상세. 요약 필드 전부 + 입력 복원(situation/memo/photoUrls) + 초안. */
data class MarketingContentDetail(
    val id: String,
    val channel: String,
    val title: String,
    val keyword: String,
    val createdAt: Instant,
    val situation: String?,
    val memo: String?,
    val photoUrls: List<String>,
    val draft: BlogDraft,
)

/**
 * 블로그 초안 수정 요청. 사장이 생성된 초안을 손봐 저장한다.
 * 입력 메타(키워드/상황/메모/사진)는 생성 시점 그대로 불변 — output(draft)만 갱신한다.
 */
data class MarketingContentUpdateRequest(
    @field:NotBlank(message = "제목을 입력해 주세요")
    @field:Size(max = 300, message = "제목이 너무 깁니다")
    val title: String?,
    // @NotNull 없으면 sections 누락(null) 시 @Size(min=1)이 발동하지 않아 빈 초안이 저장된다(Bean Validation: null=valid).
    @field:NotNull(message = "본문 단락을 입력해 주세요")
    @field:Valid
    @field:Size(min = 1, max = 30, message = "본문 단락은 1~30개여야 해요")
    val sections: List<BlogSectionInput>?,
    @field:Valid
    @field:Size(max = 30, message = "FAQ는 최대 30개까지 등록할 수 있어요")
    val faq: List<BlogFaqInput> = emptyList(),
    @field:Size(max = 30, message = "해시태그는 최대 30개까지 등록할 수 있어요")
    val hashtags: List<
        @Size(max = 100, message = "해시태그가 너무 깁니다")
        String,
    > = emptyList(),
)

/** 수정 요청의 본문 단락(소제목 + 본문). 응답용 [BlogSection]과 분리해 입력 검증을 단다. */
data class BlogSectionInput(
    @field:NotBlank(message = "소제목을 입력해 주세요")
    @field:Size(max = 300, message = "소제목이 너무 깁니다")
    val heading: String?,
    @field:NotBlank(message = "본문을 입력해 주세요")
    @field:Size(max = 10_000, message = "본문이 너무 깁니다")
    val body: String?,
)

/** 수정 요청의 FAQ 한 항목(질문/답변). 응답용 [BlogFaq]과 분리해 입력 검증을 단다. */
data class BlogFaqInput(
    @field:NotBlank(message = "질문을 입력해 주세요")
    @field:Size(max = 1_000, message = "질문이 너무 깁니다")
    val q: String?,
    @field:NotBlank(message = "답변을 입력해 주세요")
    @field:Size(max = 4_000, message = "답변이 너무 깁니다")
    val a: String?,
)
