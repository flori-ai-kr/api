package kr.ai.flori.billing

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.billing.entity.Subscription
import kr.ai.flori.billing.repository.SubscriptionRepository
import kr.ai.flori.billing.service.BillingReminderScheduler
import kr.ai.flori.common.push.PushDispatcher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Instant
import java.time.temporal.ChronoUnit

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class BillingReminderSchedulerTest {
    @Autowired lateinit var scheduler: BillingReminderScheduler

    @Autowired lateinit var subscriptionRepository: SubscriptionRepository

    @MockitoBean lateinit var pushDispatcher: PushDispatcher

    @Test
    fun `3일 뒤 결제예정 구독에 푸시 발송`() {
        val now = Instant.now()
        val inThreeDays = now.plus(3, ChronoUnit.DAYS)
        subscriptionRepository.save(
            Subscription(301L, "MONTHLY", "ACTIVE", 14900, inThreeDays),
        )
        Mockito
            .`when`(
                pushDispatcher.sendToUser(
                    ArgumentMatchers.anyLong(),
                    ArgumentMatchers.anyString(),
                    ArgumentMatchers.anyString(),
                    ArgumentMatchers.any(),
                ),
            ).thenReturn(1)

        val sent = scheduler.runReminders(now)

        assertThat(sent).isEqualTo(1)
        Mockito.verify(pushDispatcher).sendToUser(
            ArgumentMatchers.eq(301L),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.any(),
        )
    }

    @Test
    fun `10일 뒤 결제 구독엔 미발송`() {
        val now = Instant.now()
        subscriptionRepository.save(
            Subscription(302L, "MONTHLY", "ACTIVE", 14900, now.plus(10, ChronoUnit.DAYS)),
        )
        val sent = scheduler.runReminders(now)
        assertThat(sent).isEqualTo(0)
    }
}
