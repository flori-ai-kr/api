package kr.ai.flori.subscriptions.repository

import kr.ai.flori.subscriptions.entity.SubscriptionEvent
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SubscriptionEventRepository : JpaRepository<SubscriptionEvent, UUID>
