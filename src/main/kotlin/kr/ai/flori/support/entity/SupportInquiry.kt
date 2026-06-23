package kr.ai.flori.support.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseEntity
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * 1:1 문의·피드백 1건. 점주(user_id)가 작성하고 운영자가 답변/상태를 관리한다.
 * 멀티테넌시: 점주 조회는 user_id로 격리(서비스에서 강제), 운영자 조회는 cross-tenant.
 * status: open → in_progress → resolved → closed. 답변 시 [answer]가 resolved로 전이한다.
 */
@Entity
@Table(name = "support_inquiries")
class SupportInquiry(
    @Column(name = "user_id", nullable = false)
    var userId: Long,
    @Column(name = "category", nullable = false)
    var category: String,
    @Column(name = "title", nullable = false)
    var title: String,
    @Column(name = "body", nullable = false)
    var body: String,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "image_urls", columnDefinition = "text[]")
    var imageUrls: Array<String> = emptyArray()

    @Column(name = "status", nullable = false)
    var status: String = "open"

    @Column(name = "answer")
    var answer: String? = null

    @Column(name = "answered_by")
    var answeredBy: Long? = null

    @Column(name = "answered_at")
    var answeredAt: Instant? = null

    /** 운영자 답변 등록(상태를 resolved로 전이). */
    fun answer(
        text: String,
        byUserId: Long,
    ) {
        answer = text
        answeredBy = byUserId
        answeredAt = Instant.now()
        status = "resolved"
    }

    /** 상태 전이. */
    fun changeStatus(newStatus: String) {
        status = newStatus
    }
}
