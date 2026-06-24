package kr.ai.flori.ai.service

import com.fasterxml.jackson.databind.ObjectMapper
import kr.ai.flori.ai.client.AiBlogCall
import kr.ai.flori.ai.client.AiBlogDraft
import kr.ai.flori.ai.client.AiPromptOverride
import kr.ai.flori.ai.client.AiServerClient
import kr.ai.flori.ai.dto.BlogDraft
import kr.ai.flori.ai.dto.BlogFaq
import kr.ai.flori.ai.dto.BlogGenerateRequest
import kr.ai.flori.ai.dto.BlogGenerateResponse
import kr.ai.flori.ai.dto.BlogSection
import kr.ai.flori.ai.dto.MarketingContentDetail
import kr.ai.flori.ai.dto.MarketingContentSummary
import kr.ai.flori.ai.dto.MarketingContentsResponse
import kr.ai.flori.ai.dto.ToneProfileResponse
import kr.ai.flori.ai.dto.ToneProfileUpdateRequest
import kr.ai.flori.ai.entity.AiMarketingContent
import kr.ai.flori.ai.entity.AiToneProfile
import kr.ai.flori.ai.repository.AiMarketingContentRepository
import kr.ai.flori.ai.repository.AiToneProfileRepository
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.common.util.Paging
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * AI 마케팅 게이트웨이 서비스. web ↔ ai-server 중개 + tone_profile·store_context 조립 + 콘텐츠 영속.
 * 멀티테넌시: 모든 작업 TenantContext userId 격리. 외부쓰기 없음(네이버 자동업로드 미지원).
 * ai-server는 stateless — 말투/맥락/생성물 영속은 여기 DB가 소유한다.
 */
@Service
class MarketingService(
    private val aiClient: AiServerClient,
    private val toneProfileRepository: AiToneProfileRepository,
    private val contentRepository: AiMarketingContentRepository,
    private val contextBuilder: MarketingContextBuilder,
    private val usageGuard: AiUsageGuard,
    private val objectMapper: ObjectMapper,
    private val promptResolver: PromptResolver,
) {
    // @Transactional 미적용: generateBlog(LLM, ~15~40초)가 DB 커넥션을 점유하지 않게 한다(풀 고갈 방지).
    // 마케팅은 ai_chat_message를 기록하지 않아 카운트를 증가시키지 않음 → 캡은 best-effort(사전 차단만, OCR과 동일).
    fun generateBlog(
        userJwt: String,
        request: BlogGenerateRequest,
    ): BlogGenerateResponse {
        val userId = TenantContext.currentUserId()
        if (usageGuard.isOverCap(userId)) throw AppException(CommonErrorCode.FORBIDDEN, CAP_MESSAGE)

        val keyword = request.keyword.orEmpty().trim()
        val photoUrls = request.photoUrls?.filter { it.isNotBlank() }.orEmpty()
        val toneSamples = loadToneSamples(userId)
        val storeContext = contextBuilder.build(userId)
        // DB active 프롬프트(정적 부분)를 주입. 없으면 null → ai-server geo_rules.py 폴백.
        val promptOverride =
            promptResolver.resolve(CHANNEL_BLOG)?.let {
                AiPromptOverride(
                    systemMd = it.systemMd,
                    rulesMd = it.rulesMd,
                    outputSpecMd = it.outputSpecMd,
                    model = it.model,
                    temperature = it.temperature,
                )
            }

        val call =
            AiBlogCall(
                channel = CHANNEL_BLOG,
                keyword = keyword,
                situation = request.situation?.takeIf { it.isNotBlank() },
                memo = request.memo?.takeIf { it.isNotBlank() },
                photoUrls = photoUrls,
                toneSamples = toneSamples,
                storeContext = storeContext,
                promptOverride = promptOverride,
            )

        val start = System.nanoTime()
        val result = aiClient.generateBlog(userJwt, userId, call)
        val latencyMs = elapsedMs(start)

        val draft = toDraft(result.draft)
        val inputNode =
            objectMapper.createObjectNode().apply {
                put("keyword", keyword)
                request.situation?.takeIf { it.isNotBlank() }?.let { put("situation", it) }
                request.memo?.takeIf { it.isNotBlank() }?.let { put("memo", it) }
                set<com.fasterxml.jackson.databind.JsonNode>("photoUrls", objectMapper.valueToTree(photoUrls))
                storeContext?.let { set<com.fasterxml.jackson.databind.JsonNode>("storeContext", objectMapper.valueToTree(it)) }
            }

        val content =
            contentRepository.save(
                AiMarketingContent(userId, CHANNEL_BLOG).apply {
                    inputJson = inputNode
                    outputJson = objectMapper.valueToTree(draft)
                    model = result.model
                    inputTokens = result.inputTokens
                    outputTokens = result.outputTokens
                    this.latencyMs = latencyMs
                },
            )

        return BlogGenerateResponse(contentId = content.id!!.toString(), draft = draft)
    }

    @Transactional(readOnly = true)
    fun getToneProfile(): ToneProfileResponse {
        val userId = TenantContext.currentUserId()
        return ToneProfileResponse(samples = loadToneSamples(userId))
    }

    @Transactional
    fun updateToneProfile(request: ToneProfileUpdateRequest): ToneProfileResponse {
        val userId = TenantContext.currentUserId()
        val samples =
            request.samples
                .orEmpty()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .take(MAX_TONE_SAMPLES)
        val profile = toneProfileRepository.findByUserId(userId) ?: AiToneProfile(userId)
        profile.samplesJson = objectMapper.valueToTree(samples)
        toneProfileRepository.save(profile)
        return ToneProfileResponse(samples = samples)
    }

    @Transactional(readOnly = true)
    fun listContents(
        channel: String,
        offset: Int,
        limit: Int,
    ): MarketingContentsResponse {
        val userId = TenantContext.currentUserId()
        val pageable = Paging.offsetLimit(offset, limit, MAX_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt"))
        val page = contentRepository.findByUserIdAndChannelAndDeletedAtIsNull(userId, channel, pageable)
        val summaries =
            page.content.map {
                MarketingContentSummary(
                    id = it.id!!.toString(),
                    channel = it.channel,
                    title = it.outputJson.path("title").asText(""),
                    keyword = it.inputJson.path("keyword").asText(""),
                    createdAt = it.createdAt,
                )
            }
        return MarketingContentsResponse(contents = summaries, hasMore = page.hasNext())
    }

    @Transactional(readOnly = true)
    fun getContent(id: Long): MarketingContentDetail {
        val userId = TenantContext.currentUserId()
        val content =
            contentRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                ?: throw AppException(CommonErrorCode.NOT_FOUND, "콘텐츠를 찾을 수 없어요.")
        val input = content.inputJson
        val photoUrls =
            input.path("photoUrls").let { node ->
                if (node.isArray) node.mapNotNull { it.asText(null) }.filter { it.isNotBlank() } else emptyList()
            }
        return MarketingContentDetail(
            id = content.id!!.toString(),
            channel = content.channel,
            title = content.outputJson.path("title").asText(""),
            keyword = input.path("keyword").asText(""),
            createdAt = content.createdAt,
            situation = input.path("situation").asText(null)?.takeIf { it.isNotBlank() },
            memo = input.path("memo").asText(null)?.takeIf { it.isNotBlank() },
            photoUrls = photoUrls,
            draft = objectMapper.treeToValue(content.outputJson, BlogDraft::class.java),
        )
    }

    @Transactional
    fun deleteContent(id: Long) {
        val userId = TenantContext.currentUserId()
        val content =
            contentRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                ?: throw AppException(CommonErrorCode.NOT_FOUND, "콘텐츠를 찾을 수 없어요.")
        content.deletedAt = Instant.now()
        contentRepository.save(content)
    }

    private fun loadToneSamples(userId: Long): List<String> {
        val profile = toneProfileRepository.findByUserId(userId) ?: return emptyList()
        return profile.samplesJson
            .mapNotNull { it.asText(null) }
            .filter { it.isNotBlank() }
            .take(MAX_TONE_SAMPLES)
    }

    private fun toDraft(draft: AiBlogDraft): BlogDraft =
        BlogDraft(
            title = draft.title,
            sections = draft.sections.map { BlogSection(it.heading, it.body) },
            faq = draft.faq.map { BlogFaq(it.q, it.a) },
            hashtags = draft.hashtags,
        )

    private fun elapsedMs(start: Long): Int = ((System.nanoTime() - start) / NANOS_PER_MS).toInt()

    private companion object {
        const val CHANNEL_BLOG = "blog"
        const val MAX_TONE_SAMPLES = 3
        const val MAX_LIMIT = 50
        const val NANOS_PER_MS = 1_000_000L
        const val CAP_MESSAGE = "오늘 AI 사용량을 모두 사용했어요. 내일 다시 이용해 주세요."
    }
}
