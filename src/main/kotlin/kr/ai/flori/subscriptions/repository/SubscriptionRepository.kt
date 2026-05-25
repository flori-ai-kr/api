package kr.ai.flori.subscriptions.repository

import kr.ai.flori.subscriptions.entity.Subscription
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SubscriptionRepository : JpaRepository<Subscription, UUID> {
    /** 사용자당 1행. 멀티테넌시: user_id 격리. */
    fun findByUserId(userId: UUID): Subscription?
}
