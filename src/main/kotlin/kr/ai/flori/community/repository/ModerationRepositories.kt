package kr.ai.flori.community.repository

import kr.ai.flori.community.entity.CommunityBan
import kr.ai.flori.community.entity.CommunityReport
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CommunityReportRepository : JpaRepository<CommunityReport, Long> {
    /** 동일인이 같은 대상을 중복 신고했는지(보고 멱등 no-op 판정). */
    fun existsByTargetTypeAndTargetIdAndReporterUserId(
        targetType: String,
        targetId: Long,
        reporterUserId: Long,
    ): Boolean

    /** 신고 큐: 상태 필터(null=전체) + 최신순. */
    @Query(
        "SELECT r FROM CommunityReport r " +
            "WHERE (:status IS NULL OR r.status = :status) " +
            "ORDER BY r.createdAt DESC",
    )
    fun search(
        @Param("status") status: String?,
        pageable: Pageable,
    ): Page<CommunityReport>

    /** 특정 대상에 대한 특정 상태의 신고 수(큐 항목의 reportCount 표기용). */
    fun countByTargetTypeAndTargetIdAndStatus(
        targetType: String,
        targetId: Long,
        status: String,
    ): Long
}

interface CommunityBanRepository : JpaRepository<CommunityBan, Long> {
    /** 사용자의 미해제 차단(만료 여부는 호출 측이 isActive로 판정). */
    fun findByUserIdAndLiftedAtIsNull(userId: Long): CommunityBan?

    /** 활성 차단 목록(미해제 + 미만료) — 최신순. */
    @Query(
        "SELECT b FROM CommunityBan b " +
            "WHERE b.liftedAt IS NULL " +
            "AND (b.expiresAt IS NULL OR b.expiresAt > CURRENT_TIMESTAMP) " +
            "ORDER BY b.createdAt DESC",
    )
    fun findActive(pageable: Pageable): Page<CommunityBan>
}
