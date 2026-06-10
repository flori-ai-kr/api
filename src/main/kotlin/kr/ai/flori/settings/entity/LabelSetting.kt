package kr.ai.flori.settings.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.common.entity.BaseCreatedEntity

/** 라벨 설정 도메인 구분자(SSOT). */
object LabelDomains {
    const val SALE = "sale"
    const val EXPENSE = "expense"
}

/** 라벨 설정 종류 구분자(SSOT). */
object LabelKinds {
    const val CATEGORY = "category"
    const val PAYMENT = "payment"
    const val CHANNEL = "channel"
}

/**
 * 매출/지출 라벨 설정(카테고리·결제방식·채널) 단일 테이블.
 * (domain, kind)로 용도를 구분하고, (user_id, domain, kind, value) 복합 unique.
 * 멀티테넌시: 모든 쿼리 user_id 격리(HARD).
 */
@Entity
@Table(name = "label_settings")
class LabelSetting(
    @Column(name = "user_id", nullable = false)
    var userId: Long,
    @Column(name = "domain", nullable = false)
    var domain: String,
    @Column(name = "kind", nullable = false)
    var kind: String,
) : BaseCreatedEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "value", nullable = false)
    var value: String = ""

    @Column(name = "label", nullable = false)
    var label: String = ""

    @Column(name = "sort_order")
    var sortOrder: Int = 0
}
