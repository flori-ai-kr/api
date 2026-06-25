package kr.ai.flori.storage.repository

import kr.ai.flori.storage.entity.StorageIncreaseRequest
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface StorageIncreaseRequestRepository : JpaRepository<StorageIncreaseRequest, Long> {
    /** 점주 본인 요청 목록(최신순). 멀티테넌시: user_id 격리. */
    fun findByUserIdOrderByCreatedAtDesc(
        userId: Long,
        pageable: Pageable,
    ): Page<StorageIncreaseRequest>

    /** 중복 요청 방지/표시용: 본인의 특정 상태 요청들. */
    fun findByUserIdAndStatus(
        userId: Long,
        status: String,
    ): List<StorageIncreaseRequest>

    /** 운영 콘솔: 상태 필터(null=전체) 목록(최신순). cross-tenant — @RequiresAdmin 하위에서만 사용. */
    @Query(
        """
        SELECT r FROM StorageIncreaseRequest r
        WHERE (:status IS NULL OR r.status = :status)
        ORDER BY r.createdAt DESC
        """,
    )
    fun search(
        @Param("status") status: String?,
        pageable: Pageable,
    ): Page<StorageIncreaseRequest>
}
