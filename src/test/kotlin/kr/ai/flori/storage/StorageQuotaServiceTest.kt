package kr.ai.flori.storage

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.common.error.AppException
import kr.ai.flori.storage.service.StorageQuotaService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class StorageQuotaServiceTest {
    @Autowired private lateinit var service: StorageQuotaService

    private val gib = 1024L * 1024 * 1024

    @Test
    fun `행이 없으면 기본 3GiB로 get-or-create 후 사용량 0`() {
        val u = service.usage(8001L)
        assertThat(u.quotaBytes).isEqualTo(3 * gib)
        assertThat(u.usedBytes).isEqualTo(0)
        assertThat(u.percent).isEqualTo(0)
        assertThat(u.status).isEqualTo("OK")
    }

    @Test
    fun `90퍼센트 이상이면 WARN, 100퍼센트 이상이면 FULL`() {
        service.addUsage(8002L, (2.8 * gib).toLong()) // 약 93%
        assertThat(service.usage(8002L).status).isEqualTo("WARN")
        service.addUsage(8002L, 1 * gib) // 100% 초과
        assertThat(service.usage(8002L).status).isEqualTo("FULL")
    }

    @Test
    fun `한도 초과 추가는 차단된다`() {
        service.addUsage(8003L, 3 * gib) // 정확히 한도
        // 추가 1바이트도 초과 → 차단
        assertThatThrownBy { service.requireWithinQuota(8003L, 1) }
            .isInstanceOf(AppException::class.java)
        // 한도 이하 추가는 통과
        service.addUsage(8004L, 1 * gib)
        service.requireWithinQuota(8004L, 1 * gib) // 합 2GiB < 3GiB → 예외 없음
    }

    @Test
    fun `setQuota로 증설하면 한도가 늘고 차단이 풀린다`() {
        service.addUsage(8005L, 3 * gib)
        service.setQuota(8005L, 5 * gib)
        service.requireWithinQuota(8005L, 1 * gib) // 4GiB < 5GiB → 통과
        assertThat(service.usage(8005L).quotaBytes).isEqualTo(5 * gib)
    }

    @Test
    fun `reconcile는 사용량을 실제값으로 보정한다`() {
        service.addUsage(8006L, 999)
        service.reconcile(8006L, 123)
        assertThat(service.usage(8006L).usedBytes).isEqualTo(123)
    }

    @Test
    fun `정확히 90퍼센트는 WARN, 정확히 100퍼센트는 FULL`() {
        service.setQuota(8007L, 1000)
        service.addUsage(8007L, 900) // 정확히 90%
        assertThat(service.usage(8007L).status).isEqualTo("WARN")
        service.addUsage(8007L, 100) // 정확히 100%
        assertThat(service.usage(8007L).status).isEqualTo("FULL")
    }

    @Test
    fun `used + 추가 == 한도는 통과한다`() {
        service.setQuota(8008L, 1000)
        service.addUsage(8008L, 600)
        service.requireWithinQuota(8008L, 400) // 합 1000 == 한도(경계) → 예외 없음
    }
}
