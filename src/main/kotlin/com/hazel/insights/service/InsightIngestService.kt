package com.hazel.insights.service

import com.hazel.common.error.AppException
import com.hazel.common.error.ErrorCode
import com.hazel.insights.dto.IngestResultResponse
import com.hazel.insights.dto.InstagramAccountCreateRequest
import com.hazel.insights.dto.InstagramAccountResponse
import com.hazel.insights.dto.InstagramAccountUpdateRequest
import com.hazel.insights.dto.InstagramPostIngest
import com.hazel.insights.dto.TrendArticleIngest
import com.hazel.insights.entity.InstagramAccount
import com.hazel.insights.entity.InstagramPost
import com.hazel.insights.entity.TrendArticle
import com.hazel.insights.repository.InstagramAccountRepository
import com.hazel.insights.repository.InstagramPostRepository
import com.hazel.insights.repository.TrendArticleRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * 내부 수집/관리(Bearer 인증 후 호출). 트렌드·포스트 멱등 수집, 인스타 계정 관리.
 */
@Service
class InsightIngestService(
    private val trendRepository: TrendArticleRepository,
    private val postRepository: InstagramPostRepository,
    private val accountRepository: InstagramAccountRepository,
    private val broadcastService: BroadcastService,
) {
    @Transactional
    fun ingestTrends(articles: List<TrendArticleIngest>): IngestResultResponse {
        val today = LocalDate.now(KST)
        val seen = mutableSetOf<String>()
        var inserted = 0
        articles.forEach { article ->
            val url = requireNotNull(article.sourceUrl)
            if (!seen.add(url) || trendRepository.existsBySourceUrl(url)) return@forEach
            val entity =
                TrendArticle(
                    category = requireNotNull(article.category),
                    title = requireNotNull(article.title),
                    summary = requireNotNull(article.summary),
                    sourceUrl = url,
                    collectedAt = today,
                )
            entity.keyPoints = article.keyPoints
            entity.sourceName = article.sourceName
            entity.publishedAt = article.publishedAt
            trendRepository.save(entity)
            inserted++
        }
        if (inserted > 0) {
            broadcastService.broadcast("트렌드 — 새로운 인사이트 ${inserted}건", "새로운 트렌드가 도착했어요")
        }
        return IngestResultResponse(inserted, articles.size - inserted)
    }

    @Transactional
    fun ingestPosts(posts: List<InstagramPostIngest>): IngestResultResponse {
        val seen = mutableSetOf<String>()
        var inserted = 0
        posts.forEach { post ->
            val shortcode = requireNotNull(post.shortcode)
            if (!seen.add(shortcode) || postRepository.existsByShortcode(shortcode)) return@forEach
            val entity =
                InstagramPost(
                    accountId = requireNotNull(post.accountId),
                    shortcode = shortcode,
                    permalink = requireNotNull(post.permalink),
                    postedAt = requireNotNull(post.postedAt),
                )
            entity.imageUrls = post.imageUrls
            entity.caption = post.caption
            entity.likeCount = post.likeCount
            entity.scrapedAt = Instant.now()
            postRepository.save(entity)
            inserted++
        }
        return IngestResultResponse(inserted, posts.size - inserted)
    }

    @Transactional
    fun createAccount(request: InstagramAccountCreateRequest): InstagramAccountResponse {
        val username = requireNotNull(request.username)
        val account = InstagramAccount(username, "https://www.instagram.com/$username", requireNotNull(request.region))
        account.displayName = request.displayName
        account.sortOrder = request.sortOrder
        account.active = request.active
        account.notes = request.notes
        return InstagramAccountResponse.from(saveUnique(account))
    }

    @Transactional
    fun updateAccount(
        id: UUID,
        request: InstagramAccountUpdateRequest,
    ): InstagramAccountResponse {
        val account =
            accountRepository.findById(id).orElseThrow { AppException(ErrorCode.NOT_FOUND, "계정을 찾을 수 없습니다") }
        request.username?.let {
            account.username = it
            account.profileUrl = "https://www.instagram.com/$it"
        }
        request.displayName?.let { account.displayName = it }
        request.region?.let { account.region = it }
        request.sortOrder?.let { account.sortOrder = it }
        request.active?.let { account.active = it }
        request.notes?.let { account.notes = it }
        account.updatedAt = Instant.now()
        return InstagramAccountResponse.from(saveUnique(account))
    }

    @Transactional
    fun deleteAccount(id: UUID) {
        if (!accountRepository.existsById(id)) throw AppException(ErrorCode.NOT_FOUND, "계정을 찾을 수 없습니다")
        accountRepository.deleteById(id)
    }

    private fun saveUnique(account: InstagramAccount): InstagramAccount =
        try {
            accountRepository.saveAndFlush(account)
        } catch (_: DataIntegrityViolationException) {
            throw AppException(ErrorCode.DUPLICATE, "이미 등록된 계정입니다")
        }

    private companion object {
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
