package kr.ai.flori.billing

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.billing.entity.Subscription
import kr.ai.flori.billing.repository.SubscriptionRepository
import kr.ai.flori.billing.service.AdminSubscriptionService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class AdminSubscriptionTest {
    @Autowired lateinit var service: AdminSubscriptionService

    @Autowired lateinit var subscriptionRepository: SubscriptionRepository

    @AfterEach fun cleanup() = subscriptionRepository.deleteAll()

    private fun sub(
        userId: Long,
        status: String,
    ) {
        subscriptionRepository.save(Subscription(userId, "MONTHLY", status, 14900, Instant.now()))
    }

    @Test
    fun `counts 는 상태별 집계`() {
        sub(1L, "ACTIVE")
        sub(2L, "ACTIVE")
        sub(3L, "TRIALING")
        sub(4L, "IN_GRACE")
        sub(5L, "EXPIRED")
        val c = service.counts()
        assertThat(c.active).isEqualTo(2)
        assertThat(c.trialing).isEqualTo(1)
        assertThat(c.inGrace).isEqualTo(1)
        assertThat(c.expired).isEqualTo(1)
    }

    @Test
    fun `list 는 status 필터`() {
        sub(1L, "ACTIVE")
        sub(2L, "EXPIRED")
        assertThat(service.list("ACTIVE", 0, 50)).hasSize(1)
        assertThat(service.list(null, 0, 50)).hasSize(2)
    }
}
