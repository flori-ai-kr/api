package kr.ai.flori.support.repository

import kr.ai.flori.support.entity.SupportInquiry
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SupportInquiryRepository : JpaRepository<SupportInquiry, Long> {
    /** 점주 본인 문의 목록(최신순). 멀티테넌시: 항상 userId로 격리. */
    fun findByUserIdOrderByCreatedAtDesc(
        userId: Long,
        pageable: Pageable,
    ): Page<SupportInquiry>

    /** 운영 콘솔: 상태 필터(null이면 전체) 문의 목록(최신순). cross-tenant — @RequiresAdmin 하위에서만 사용. */
    @Query(
        """
        SELECT i FROM SupportInquiry i
        WHERE (:status IS NULL OR i.status = :status)
        ORDER BY i.createdAt DESC
        """,
    )
    fun search(
        @Param("status") status: String?,
        pageable: Pageable,
    ): Page<SupportInquiry>
}
