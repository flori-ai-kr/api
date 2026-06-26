package kr.ai.flori.storage

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.storage.entity.StorageIncreaseRequest
import kr.ai.flori.storage.repository.StorageIncreaseRequestRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class StorageIncreaseRequestRepositoryTest {
    @Autowired private lateinit var repository: StorageIncreaseRequestRepository

    @Test
    fun `요청은 PENDING으로 저장되고 approve로 APPROVED 전이된다`() {
        val saved = repository.save(StorageIncreaseRequest(userId = 7001L, reason = "사진 많아요"))
        assertThat(saved.status).isEqualTo("PENDING")
        saved.approve(5L * 1024 * 1024 * 1024)
        repository.save(saved)
        val reloaded = repository.findById(saved.id!!).get()
        assertThat(reloaded.status).isEqualTo("APPROVED")
        assertThat(reloaded.resolvedBytes).isEqualTo(5L * 1024 * 1024 * 1024)
    }

    @Test
    fun `reject로 REJECTED 전이되고 사유가 저장된다`() {
        val saved = repository.save(StorageIncreaseRequest(userId = 7006L, reason = "더 필요"))
        saved.reject("불필요한 요청")
        repository.save(saved)
        val reloaded = repository.findById(saved.id!!).get()
        assertThat(reloaded.status).isEqualTo("REJECTED")
        assertThat(reloaded.rejectReason).isEqualTo("불필요한 요청")
    }

    @Test
    fun `status 필터 검색은 PENDING만 반환한다`() {
        repository.save(StorageIncreaseRequest(userId = 7002L, reason = "a"))
        repository.save(StorageIncreaseRequest(userId = 7003L, reason = "b").apply { approve(1) })
        val pending = repository.search("PENDING", PageRequest.of(0, 50))
        assertThat(pending.content).allMatch { it.status == "PENDING" }
        assertThat(pending.content).isNotEmpty
    }

    @Test
    fun `status가 null이면 모든 상태를 반환한다`() {
        repository.save(StorageIncreaseRequest(userId = 7004L, reason = "c"))
        repository.save(StorageIncreaseRequest(userId = 7005L, reason = "d").apply { approve(1) })
        val all = repository.search(null, PageRequest.of(0, 50))
        assertThat(all.content).anyMatch { it.status == "PENDING" }
        assertThat(all.content).anyMatch { it.status == "APPROVED" }
    }
}
