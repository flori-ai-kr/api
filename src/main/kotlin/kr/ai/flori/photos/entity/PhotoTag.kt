package kr.ai.flori.photos.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseCreatedEntity

/**
 * 사진 태그. (name, user_id) 복합 unique. 멀티테넌시: user_id 격리.
 */
@Entity
@Table(name = "photo_tags")
class PhotoTag(
    @Column(name = "user_id", nullable = false)
    var userId: Long,
    @Column(name = "name", nullable = false)
    var name: String,
) : BaseCreatedEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
}
