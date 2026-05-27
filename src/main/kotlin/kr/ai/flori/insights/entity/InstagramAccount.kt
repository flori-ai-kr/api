package kr.ai.flori.insights.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseEntity

/**
 * 팔로우 인스타그램 계정. 공유 데이터. 쓰기는 내부 API만.
 */
@Entity
@Table(name = "instagram_accounts")
class InstagramAccount(
    @Column(name = "username", nullable = false)
    var username: String,
    @Column(name = "profile_url", nullable = false)
    var profileUrl: String,
    @Column(name = "region", nullable = false)
    var region: String,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "display_name")
    var displayName: String? = null

    @Column(name = "sort_order")
    var sortOrder: Int = 0

    @Column(name = "active")
    var active: Boolean = true

    @Column(name = "notes")
    var notes: String? = null
}
