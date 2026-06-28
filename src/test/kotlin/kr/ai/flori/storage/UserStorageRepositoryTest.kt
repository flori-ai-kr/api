package kr.ai.flori.storage

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.storage.entity.UserStorage
import kr.ai.flori.storage.repository.UserStorageRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class UserStorageRepositoryTest {
    @Autowired private lateinit var repository: UserStorageRepository

    @Test
    fun `기본 한도 3GiB로 저장되고 user_id로 조회된다`() {
        val saved = repository.save(UserStorage(userId = 9001L))
        assertThat(saved.quotaBytes).isEqualTo(3L * 1024 * 1024 * 1024)
        assertThat(saved.usedBytes).isEqualTo(0)
        assertThat(repository.findByUserId(9001L)).isNotNull
    }

    @Test
    fun `addUsedBytes는 원자적으로 증감하고 0 미만으로 내려가지 않는다`() {
        repository.save(UserStorage(userId = 9002L))
        repository.addUsedBytes(9002L, 500)
        assertThat(repository.findByUserId(9002L)!!.usedBytes).isEqualTo(500)
        repository.addUsedBytes(9002L, -1000) // 음수로 내려가면 0 클램프
        assertThat(repository.findByUserId(9002L)!!.usedBytes).isEqualTo(0)
    }

    @Test
    fun `setUsedBytes는 정합 시 절대값으로 덮어쓴다`() {
        repository.save(UserStorage(userId = 9003L))
        repository.addUsedBytes(9003L, 1234)
        repository.setUsedBytes(9003L, 777)
        assertThat(repository.findByUserId(9003L)!!.usedBytes).isEqualTo(777)
    }
}
