package kr.ai.flori.insights.service

import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.insights.dto.InsightScrapResponse
import kr.ai.flori.insights.dto.InstagramPostResponse
import kr.ai.flori.insights.dto.PostScrapResponse
import kr.ai.flori.insights.dto.ScrapCountsResponse
import kr.ai.flori.insights.dto.ScrapInfo
import kr.ai.flori.insights.dto.TrendArticleResponse
import kr.ai.flori.insights.dto.TrendScrapResponse
import kr.ai.flori.insights.entity.InsightScrap
import kr.ai.flori.insights.repository.InsightScrapRepository
import kr.ai.flori.insights.repository.InstagramAccountRepository
import kr.ai.flori.insights.repository.InstagramPostRepository
import kr.ai.flori.insights.repository.TrendArticleRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 인사이트 스크랩(polymorphic). 멀티테넌시: user_id 격리(HARD).
 */
@Service
class ScrapService(
    private val scrapRepository: InsightScrapRepository,
    private val trendRepository: TrendArticleRepository,
    private val postRepository: InstagramPostRepository,
    private val accountRepository: InstagramAccountRepository,
) {
    @Transactional
    fun toggle(
        targetType: String,
        targetId: Long,
    ): Boolean {
        val userId = TenantContext.currentUserId()
        val type = validType(targetType)
        val existing = scrapRepository.findByUserIdAndTargetTypeAndTargetId(userId, type, targetId)
        if (existing != null) {
            scrapRepository.delete(existing)
            return false
        }
        requireTargetExists(type, targetId)
        return try {
            scrapRepository.saveAndFlush(InsightScrap(userId, type, targetId))
            true
        } catch (_: DataIntegrityViolationException) {
            true // 동시 토글 레이스 — 이미 스크랩됨으로 간주
        }
    }

    @Transactional
    fun updateMemo(
        targetType: String,
        targetId: Long,
        memo: String?,
    ): InsightScrapResponse {
        val userId = TenantContext.currentUserId()
        val scrap =
            scrapRepository.findByUserIdAndTargetTypeAndTargetId(userId, validType(targetType), targetId)
                ?: throw AppException(CommonErrorCode.NOT_FOUND, "먼저 스크랩한 후 메모를 저장할 수 있어요")
        scrap.memo = memo?.takeIf { it.isNotBlank() }
        return InsightScrapResponse.from(scrapRepository.save(scrap))
    }

    @Transactional(readOnly = true)
    fun scrapMap(targetType: String): Map<Long, ScrapInfo> =
        scrapRepository
            .findByUserIdAndTargetType(TenantContext.currentUserId(), validType(targetType))
            .associate { it.targetId to ScrapInfo(requireNotNull(it.id), it.memo) }

    @Transactional(readOnly = true)
    fun counts(): ScrapCountsResponse {
        val userId = TenantContext.currentUserId()
        return ScrapCountsResponse(
            trend = scrapRepository.countByUserIdAndTargetType(userId, TYPE_TREND),
            post = scrapRepository.countByUserIdAndTargetType(userId, TYPE_POST),
        )
    }

    @Transactional(readOnly = true)
    fun trendScraps(limit: Int): List<TrendScrapResponse> {
        val scraps = loadScraps(TYPE_TREND, limit)
        if (scraps.isEmpty()) return emptyList()
        val articles = trendRepository.findAllById(scraps.map { it.targetId }).associateBy { it.id }
        return scraps.mapNotNull { scrap ->
            articles[scrap.targetId]?.let {
                TrendScrapResponse(InsightScrapResponse.from(scrap), TrendArticleResponse.from(it))
            }
        }
    }

    @Transactional(readOnly = true)
    fun postScraps(limit: Int): List<PostScrapResponse> {
        val scraps = loadScraps(TYPE_POST, limit)
        if (scraps.isEmpty()) return emptyList()
        val posts = postRepository.findByIdIn(scraps.map { it.targetId }).associateBy { it.id }
        // FK 연관관계 대신 accountId로 계정을 별도 조회해 합친다(간접참조).
        val accountsById = accountRepository.findAllById(posts.values.map { it.accountId }.toSet()).associateBy { it.id }
        return scraps.mapNotNull { scrap ->
            posts[scrap.targetId]?.let {
                PostScrapResponse(
                    InsightScrapResponse.from(scrap),
                    InstagramPostResponse.from(it, accountsById[it.accountId]),
                )
            }
        }
    }

    private fun loadScraps(
        type: String,
        limit: Int,
    ): List<InsightScrap> =
        scrapRepository.findByUserIdAndTargetTypeOrderByCreatedAtDesc(
            TenantContext.currentUserId(),
            type,
            PageRequest.of(0, limit.coerceIn(1, MAX_LIMIT)),
        )

    private fun requireTargetExists(
        type: String,
        targetId: Long,
    ) {
        val exists = if (type == TYPE_TREND) trendRepository.existsById(targetId) else postRepository.existsById(targetId)
        if (!exists) {
            throw AppException(CommonErrorCode.NOT_FOUND, if (type == TYPE_TREND) "존재하지 않는 트렌드입니다" else "존재하지 않는 포스트입니다")
        }
    }

    private fun validType(targetType: String): String {
        if (targetType !in TYPES) throw AppException(CommonErrorCode.VALIDATION, "올바르지 않은 대상 유형입니다")
        return targetType
    }

    private companion object {
        const val TYPE_TREND = "trend"
        const val TYPE_POST = "post"
        const val MAX_LIMIT = 200
        val TYPES = setOf(TYPE_TREND, TYPE_POST)
    }
}
