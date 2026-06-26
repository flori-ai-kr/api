package kr.ai.flori.storage

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.photos.dto.PhotoCardCreateRequest
import kr.ai.flori.photos.entity.PhotoFile
import kr.ai.flori.photos.service.PhotoCardService
import kr.ai.flori.storage.job.StorageReconcileJob
import kr.ai.flori.storage.service.StorageQuotaService
import kr.ai.flori.support.TestTenants
import kr.ai.flori.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class StorageReconcileJobTest {
    @Autowired private lateinit var job: StorageReconcileJob

    @Autowired private lateinit var quotaService: StorageQuotaService

    @Autowired private lateinit var photoCardService: PhotoCardService

    @Autowired private lateinit var authService: AuthService

    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    @Autowired private lateinit var userRepository: UserRepository

    @Test
    fun `정합 작업은 드리프트된 사용량을 DB 실측 합으로 보정한다`() {
        val userId = TestTenants.bootstrap(authService, tokenProvider, userRepository)
        photoCardService.create(
            PhotoCardCreateRequest(
                title = "t",
                memo = null,
                tags = emptyList(),
                photos = listOf(PhotoFile(url = "https://cdn/photo-cards/u$userId/x.jpg", originalName = "x.jpg", size = 4000)),
                saleId = null,
                customerId = null,
            ),
        )
        // 인위적 드리프트 주입
        quotaService.addUsage(userId, 99999)
        assertThat(quotaService.usage(userId).usedBytes).isEqualTo(4000 + 99999)

        job.runReconcile()

        assertThat(quotaService.usage(userId).usedBytes).isEqualTo(4000)
    }
}
