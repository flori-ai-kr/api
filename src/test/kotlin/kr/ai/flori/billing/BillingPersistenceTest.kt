package kr.ai.flori.billing

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.billing.entity.BillingKey
import kr.ai.flori.billing.entity.Subscription
import kr.ai.flori.billing.repository.BillingKeyRepository
import kr.ai.flori.billing.repository.SubscriptionRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant

/**
 * 빌링 엔티티 ↔ 스키마(all-tables-ddl.sql) 정합 + 암호화 컬럼 라운드트립 검증.
 * 컨텍스트 로딩 자체가 ddl-auto=validate 통과(엔티티/스키마 일치)를 보장한다.
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class BillingPersistenceTest {
    @Autowired lateinit var billingKeyRepository: BillingKeyRepository

    @Autowired lateinit var subscriptionRepository: SubscriptionRepository

    @Test
    fun `billingKey 저장-조회 + 암호문 컬럼 라운드트립`() {
        val saved =
            billingKeyRepository.save(
                BillingKey(userId = 1L, customerKey = "cust-1", billingKey = "bk_secret_123").apply {
                    cardCompany = "신한"
                    cardNumberMasked = "1234"
                    cardType = "체크"
                },
            )
        val found = billingKeyRepository.findByUserId(1L)
        assertThat(found).isNotNull
        assertThat(found!!.billingKey).isEqualTo("bk_secret_123") // 복호화되어 원문
        assertThat(found.customerKey).isEqualTo("cust-1")
        assertThat(saved.id).isNotNull
    }

    @Test
    fun `subscription 저장-조회`() {
        val now = Instant.now()
        subscriptionRepository.save(
            Subscription(
                userId = 2L,
                plan = "MONTHLY",
                status = "TRIALING",
                amount = 14900,
                nextBillingAt = now,
            ),
        )
        val found = subscriptionRepository.findByUserId(2L)
        assertThat(found).isNotNull
        assertThat(found!!.status).isEqualTo("TRIALING")
        assertThat(found.amount).isEqualTo(14900)
    }
}
