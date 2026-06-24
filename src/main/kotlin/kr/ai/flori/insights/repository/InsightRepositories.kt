package kr.ai.flori.insights.repository

import kr.ai.flori.insights.entity.FlowerAuctionPrice
import kr.ai.flori.insights.entity.FlowerItemScrap
import kr.ai.flori.insights.entity.InsightScrap
import kr.ai.flori.insights.entity.SupportProgram
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * 화훼 경매 시세(공유 읽기). 단순 CRUD/적재용 — 파생(등락률) 조회는 FlowerAuctionPriceQueryRepository.
 * 선언 쿼리 메서드가 없어 격리 가드 스캔 대상이 아니다(파생은 별도 @Repository 네이티브).
 */
interface FlowerAuctionPriceRepository : JpaRepository<FlowerAuctionPrice, Long>

/**
 * 지원사업(공유 읽기). 테넌트 격리 없음 — 의도적 전역.
 */
interface SupportProgramRepository : JpaRepository<SupportProgram, Long> {
    /**
     * 목록: 카테고리 필터(없으면 전체) + 모집중(마감 미경과 또는 상시[null])만 + 마감 임박순(nulls last).
     * 마감 지난 공고는 노출에서 제외한다(만료 공고가 임박순 앞에 깔리는 문제 방지).
     * keyword(빈 문자열이면 전체)로 제목·요약·기관명을 대소문자 무시 부분일치 검색한다.
     * (null 바인드는 CONCAT 타입추론을 깨므로 호출부에서 빈 문자열로 정규화 — 빈값은 제목 LIKE '%%'로 전체 매칭.)
     */
    @Query(
        "SELECT p FROM SupportProgram p " +
            "WHERE (:category IS NULL OR p.category = :category) " +
            "AND (p.applyEnd IS NULL OR p.applyEnd >= CURRENT_DATE) " +
            "AND (LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(p.summary) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(p.agency) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "ORDER BY p.applyEnd ASC NULLS LAST, p.id DESC",
    )
    fun findFeed(
        @Param("category") category: String?,
        @Param("keyword") keyword: String,
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

/**
 * 경매 품목 스크랩(개인). 모든 메서드는 user_id 로 격리한다(멀티테넌시 HARD).
 */
interface FlowerItemScrapRepository : JpaRepository<FlowerItemScrap, Long> {
    fun findByUserIdAndPumName(
        userId: Long,
        pumName: String,
    ): FlowerItemScrap?

    /** 내 스크랩 품목 목록(최신순). */
    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<FlowerItemScrap>
}
