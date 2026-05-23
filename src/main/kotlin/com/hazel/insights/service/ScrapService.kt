package com.hazel.insights.service

import com.hazel.common.error.AppException
import com.hazel.common.error.ErrorCode
import com.hazel.common.tenant.TenantContext
import com.hazel.insights.dto.InsightScrapResponse
import com.hazel.insights.dto.InstagramPostResponse
import com.hazel.insights.dto.PostScrapResponse
import com.hazel.insights.dto.ScrapCountsResponse
import com.hazel.insights.dto.ScrapInfo
import com.hazel.insights.dto.TrendArticleResponse
import com.hazel.insights.dto.TrendScrapResponse
import com.hazel.insights.entity.InsightScrap
import com.hazel.insights.repository.InsightScrapRepository
import com.hazel.insights.repository.InstagramPostRepository
import com.hazel.insights.repository.TrendArticleRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * 인사이트 스크랩(polymorphic). 멀티테넌시: user_id 격리(HARD).
 */
@Service
class ScrapService(
    private val scrapRepository: InsightScrapRepository,
    private val trendRepository: TrendArticleRepository,
    private val postRepository: InstagramPostRepository,
) {
    @Transactional
    fun toggle(
        targetType: String,
        targetId: UUID,
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
        targetId: UUID,
        memo: String?,
    ): InsightScrapResponse {
        val userId = TenantContext.currentUserId()
        val scrap =
            scrapRepository.findByUserIdAndTargetTypeAndTargetId(userId, validType(targetType), targetId)
                ?: throw AppException(ErrorCode.NOT_FOUND, "먼저 스크랩한 후 메모를 저장할 수 있어요")
        scrap.memo = memo?.takeIf { it.isNotBlank() }
        scrap.updatedAt = Instant.now()
        return InsightScrapResponse.from(scrapRepository.save(scrap))
    }

    @Transactional(readOnly = true)
    fun scrapMap(targetType: String): Map<UUID, ScrapInfo> =
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
        val posts = postRepository.findWithAccountByIdIn(scraps.map { it.targetId }).associateBy { it.id }
        return scraps.mapNotNull { scrap ->
            posts[scrap.targetId]?.let {
                PostScrapResponse(InsightScrapResponse.from(scrap), InstagramPostResponse.from(it))
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
        targetId: UUID,
    ) {
        val exists = if (type == TYPE_TREND) trendRepository.existsById(targetId) else postRepository.existsById(targetId)
        if (!exists) {
            throw AppException(ErrorCode.NOT_FOUND, if (type == TYPE_TREND) "존재하지 않는 트렌드입니다" else "존재하지 않는 포스트입니다")
        }
    }

    private fun validType(targetType: String): String {
        if (targetType !in TYPES) throw AppException(ErrorCode.VALIDATION, "올바르지 않은 대상 유형입니다")
        return targetType
    }

    private companion object {
        const val TYPE_TREND = "trend"
        const val TYPE_POST = "post"
        const val MAX_LIMIT = 200
        val TYPES = setOf(TYPE_TREND, TYPE_POST)
    }
}
