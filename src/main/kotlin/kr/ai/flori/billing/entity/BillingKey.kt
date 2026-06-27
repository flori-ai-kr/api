package kr.ai.flori.billing.entity

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kr.ai.flori.billing.crypto.BillingKeyCryptoConverter
import kr.ai.flori.common.entity.BaseEntity

/** 유저당 카드 1장(토스 빌링키). billing_key는 AES 암호화 저장. */
@Entity
@Table(name = "billing_key")
class BillingKey(
    @Column(name = "user_id", nullable = false)
    var userId: Long,
    @Column(name = "customer_key", nullable = false)
    var customerKey: String,
    @Convert(converter = BillingKeyCryptoConverter::class)
    @Column(name = "billing_key", nullable = false)
    var billingKey: String,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "card_company")
    var cardCompany: String? = null

    @Column(name = "card_number_masked")
    var cardNumberMasked: String? = null

    @Column(name = "card_type")
    var cardType: String? = null

    @Column(name = "status", nullable = false)
    var status: String = "ACTIVE"
}
