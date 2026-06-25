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
    fun `요청은 PENDING으로 저장되고 resolve로 RESOLVED 전이된다`() {
        val saved = repository.save(StorageIncreaseRequest(userId = 7001L, reason = "사진 많아요"))
        assertThat(saved.status).isEqualTo("PENDING")
        saved.resolve(5L * 1024 * 1024 * 1024)
        repository.save(saved)
        val reloaded = repository.findById(saved.id!!).get()
        assertThat(reloaded.status).isEqualTo("RESOLVED")
        assertThat(reloaded.resolvedBytes).isEqualTo(5L * 1024 * 1024 * 1024)
    }

    @Test
    fun `status 필터 검색은 PENDING만 반환한다`() {
        repository.save(StorageIncreaseRequest(userId = 7002L, reason = "a"))
        repository.save(StorageIncreaseRequest(userId = 7003L, reason = "b").apply { resolve(1) })
        val pending = repository.search("PENDING", PageRequest.of(0, 50))
        assertThat(pending.content).allMatch { it.status == "PENDING" }
        assertThat(pending.content).isNotEmpty
    }
}
