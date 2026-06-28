package kr.ai.flori.announcement.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseEntity
import java.time.Instant

/**
 * 공지 배너 1건(운영자 CMS). 점주 앱 상단/모달로 노출되는 운영 공지.
 * 노출 제어: is_active + (starts_at, ends_at) 기간. soft delete(deleted_at).
 * placement = 'modal'(모달) | 'bar'(상단 바). 상태 전이는 도메인 메서드로만 한다.
 */
@Entity
@Table(name = "announcements")
class Announcement(
    @Column(name = "placement", nullable = false)
    var placement: String = "modal",
    @Column(name = "title", nullable = false)
    var title: String,
    @Column(name = "created_by", nullable = false)
    var createdBy: Long,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "body")
    var body: String? = null

    @Column(name = "image_url")
    var imageUrl: String? = null

    @Column(name = "link_url")
    var linkUrl: String? = null

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = false

    @Column(name = "starts_at")
    var startsAt: Instant? = null

    @Column(name = "ends_at")
    var endsAt: Instant? = null

    @Column(name = "click_count", nullable = false)
    var clickCount: Int = 0

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null

    /** 노출 활성화. */
    fun activate() {
        isActive = true
    }

    /** 노출 비활성화. */
    fun deactivate() {
        isActive = false
    }

    /** soft delete(삭제 시각 기록). */
    fun softDelete() {
        deletedAt = Instant.now()
    }

    /** 클릭 카운트 1 증가. */
    fun incrementClick() {
        clickCount++
    }
}
