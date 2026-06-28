package kr.ai.flori.billing.repository

import kr.ai.flori.billing.entity.SubscriptionEligibility
import org.springframework.data.jpa.repository.JpaRepository

interface SubscriptionEligibilityRepository : JpaRepository<SubscriptionEligibility, Long> {
    fun findByIdentityHash(identityHash: String): SubscriptionEligibility?
}
