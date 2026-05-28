package kr.ai.flori.photos.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseEntity
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/** 사진 파일(jsonb 배열 요소). */
data class PhotoFile(
    val url: String = "",
    val originalName: String = "",
)

/**
 * 사진 카드. 매출(sale_id) 연동. tags(TEXT[])·photos(jsonb)는 Hibernate 6 네이티브 매핑.
 * 멀티테넌시: user_id 격리.
 */
@Entity
@Table(name = "photo_cards")
class PhotoCard(
    @Column(name = "user_id", nullable = false)
    var userId: Long,
    @Column(name = "title", nullable = false)
    var title: String,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "description")
    var description: String? = null

    // Array<String>(네이티브 ARRAY) — List<String>(JSON)과 다른 자바 타입이라 전역 타입 충돌 회피
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", columnDefinition = "text[]")
    var tags: Array<String> = emptyArray()

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "photos", columnDefinition = "jsonb")
    var photos: List<PhotoFile> = emptyList()

    @Column(name = "sale_id")
    var saleId: Long? = null
}
