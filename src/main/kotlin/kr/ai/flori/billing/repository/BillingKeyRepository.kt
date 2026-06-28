package kr.ai.flori.billing.repository

import kr.ai.flori.billing.entity.BillingKey
import org.springframework.data.jpa.repository.JpaRepository

interface BillingKeyRepository : JpaRepository<BillingKey, Long> {
    fun findByUserId(userId: Long): BillingKey?
}
