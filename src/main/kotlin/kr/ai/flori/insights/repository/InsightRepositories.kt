package kr.ai.flori.insights.repository

import kr.ai.flori.insights.entity.FlowerAuctionPrice
import kr.ai.flori.insights.entity.InsightScrap
import kr.ai.flori.insights.entity.SupportProgram
import kr.ai.flori.insights.entity.TrendArticle
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * 트렌드 기사(공유 읽기). 테넌트 격리 없음 — 의도적 전역(TenantIsolationGuardTest 화이트리스트 등록).
 */
interface TrendArticleRepository : JpaRepository<TrendArticle, Long> {
    /** 목록: 카테고리 필터(없으면 전체) + 수집일/생성순 최신. */
    @Query(
        "SELECT a FROM TrendArticle a " +
            "WHERE (:category IS NULL OR a.category = :category) " +
            "ORDER BY a.collectedAt DESC, a.id DESC",
    )
    fun findFeed(
        @Param("category") category: String?,
        pageable: Pageable,
    ): Page<TrendArticle>

    /** 단건(스크랩 대상 검증·목록 enrichment 용). 공유 데이터라 id 만으로 조회. */
    fun findByIdIn(ids: Collection<Long>): List<TrendArticle>
}

/**
 * 화훼 경매 시세(공유 읽기). 단순 CRUD/적재용 — 파생(등락률) 조회는 FlowerAuctionPriceQueryRepository.
 * 선언 쿼리 메서드가 없어 격리 가드 스캔 대상이 아니다(파생은 별도 @Repository 네이티브).
 */
interface FlowerAuctionPriceRepository : JpaRepository<FlowerAuctionPrice, Long>

/**
 * 지원사업(공유 읽기). 테넌트 격리 없음 — 의도적 전역.
 */
interface SupportProgramRepository : JpaRepository<SupportProgram, Long> {
    /** 목록: 카테고리 필터(없으면 전체) + 마감(apply_end) 임박순(nulls last). */
    @Query(
        "SELECT p FROM SupportProgram p " +
            "WHERE (:category IS NULL OR p.category = :category) " +
            "ORDER BY p.applyEnd ASC NULLS LAST, p.id DESC",
    )
    fun findFeed(
        @Param("category") category: String?,
        pageable: Pageable,
    ): Page<SupportProgram>

    fun findByIdIn(ids: Collection<Long>): List<SupportProgram>
}

/**
 * 인사이트 스크랩(개인). 모든 메서드는 user_id 로 격리한다(멀티테넌시 HARD).
 */
interface InsightScrapRepository : JpaRepository<InsightScrap, Long> {
    fun findByUserIdAndTargetTypeAndTargetId(
        userId: Long,
        targetType: String,
        targetId: Long,
    ): InsightScrap?

    /** 특정 대상 유형의 내 스크랩 목록(최신순). */
    fun findByUserIdAndTargetTypeOrderByCreatedAtDesc(
        userId: Long,
        targetType: String,
    ): List<InsightScrap>

    fun countByUserIdAndTargetType(
        userId: Long,
        targetType: String,
    ): Long
}
