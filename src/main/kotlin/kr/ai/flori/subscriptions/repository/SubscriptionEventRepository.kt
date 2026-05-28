package kr.ai.flori.subscriptions.repository

import kr.ai.flori.subscriptions.entity.SubscriptionEvent
import org.springframework.data.jpa.repository.JpaRepository

interface SubscriptionEventRepository : JpaRepository<SubscriptionEvent, Long>
