package kr.ai.flori.waitlist.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseCreatedEntity

/**
 * 사전등록 1건. 인증/테넌시와 무관한 공개 모집 데이터.
 * email은 정규화(trim+소문자)하여 저장하며 UNIQUE로 중복 등록을 막는다.
 * email·shopName 모두 필수.
 */
@Entity
@Table(name = "waitlist_registrations")
class WaitlistRegistration(
    @Column(name = "email", nullable = false, unique = true)
    var email: String,
    @Column(name = "shop_name", nullable = false)
    var shopName: String,
) : BaseCreatedEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
}
