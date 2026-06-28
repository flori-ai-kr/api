package kr.ai.flori.announcement.repository

import kr.ai.flori.announcement.entity.Announcement
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface AnnouncementRepository : JpaRepository<Announcement, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): Announcement?

    /** 운영자 목록: soft delete 제외, 최신순. */
    @Query(
        """
        SELECT a FROM Announcement a
        WHERE a.deletedAt IS NULL
        ORDER BY a.createdAt DESC
        """,
    )
    fun findAllForAdmin(pageable: Pageable): Page<Announcement>

    /** 점주용 노출 목록: 활성 + 기간 내 + (선택)placement 일치, 최신순. */
    @Query(
        """
        SELECT a FROM Announcement a
        WHERE a.deletedAt IS NULL
          AND a.isActive = true
          AND (:placement IS NULL OR a.placement = :placement)
          AND (a.startsAt IS NULL OR a.startsAt <= :now)
          AND (a.endsAt IS NULL OR a.endsAt >= :now)
        ORDER BY a.createdAt DESC
        """,
    )
    fun findActive(
        @Param("placement") placement: String?,
        @Param("now") now: Instant,
    ): List<Announcement>
}
