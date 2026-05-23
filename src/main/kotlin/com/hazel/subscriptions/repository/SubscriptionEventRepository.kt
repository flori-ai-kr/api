package com.hazel.subscriptions.repository

import com.hazel.subscriptions.entity.SubscriptionEvent
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SubscriptionEventRepository : JpaRepository<SubscriptionEvent, UUID>
