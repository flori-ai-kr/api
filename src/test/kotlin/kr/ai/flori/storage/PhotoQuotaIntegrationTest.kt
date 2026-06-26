package kr.ai.flori.storage

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.photos.dto.FileMetaRequest
import kr.ai.flori.photos.dto.PhotoCardCreateRequest
import kr.ai.flori.photos.entity.PhotoFile
import kr.ai.flori.photos.service.PhotoCardService
import kr.ai.flori.storage.service.StorageQuotaService
import kr.ai.flori.support.TestTenants
import kr.ai.flori.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class PhotoQuotaIntegrationTest {
    @Autowired private lateinit var photoCardService: PhotoCardService

    @Autowired private lateinit var quotaService: StorageQuotaService

    @Autowired private lateinit var authService: AuthService

    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    @Autowired private lateinit var userRepository: UserRepository

    @AfterEach
    fun tearDown() = TenantContext.clear()

    @Test
    fun `카드 생성 시 사진 size 합이 사용량에 더해진다`() {
        val userId = TestTenants.bootstrap(authService, tokenProvider, userRepository)
        photoCardService.create(
            PhotoCardCreateRequest(
                title = "꽃다발",
                memo = null,
                tags = emptyList(),
                photos =
                    listOf(
                        PhotoFile(url = "https://cdn/photo-cards/u$userId/a.jpg", originalName = "a.jpg", size = 1000),
                        PhotoFile(url = "https://cdn/photo-cards/u$userId/b.jpg", originalName = "b.jpg", size = 2000),
                    ),
                saleId = null,
                customerId = null,
            ),
        )
        assertThat(quotaService.usage(userId).usedBytes).isEqualTo(3000)
    }

    @Test
    fun `presign 발급 시 한도 초과면 차단된다`() {
        val userId = TestTenants.bootstrap(authService, tokenProvider, userRepository)
        quotaService.setQuota(userId, 5000)
        quotaService.addUsage(userId, 4500)
        assertThatThrownBy {
            photoCardService.createUploadTargets(
                listOf(FileMetaRequest(name = "big.jpg", type = "image/jpeg", size = 1000)),
            )
        }.isInstanceOf(AppException::class.java)
    }

    @Test
    fun `카드 삭제 시 사용량이 감분된다`() {
        val userId = TestTenants.bootstrap(authService, tokenProvider, userRepository)
        val card =
            photoCardService.create(
                PhotoCardCreateRequest(
                    title = "삭제용",
                    memo = null,
                    tags = emptyList(),
                    photos = listOf(PhotoFile(url = "https://cdn/photo-cards/u$userId/c.jpg", originalName = "c.jpg", size = 5000)),
                    saleId = null,
                    customerId = null,
                ),
            )
        assertThat(quotaService.usage(userId).usedBytes).isEqualTo(5000)
        photoCardService.delete(card.id)
        assertThat(quotaService.usage(userId).usedBytes).isEqualTo(0)
    }

    @Test
    fun `reorder로 사진을 추가하면 사용량 델타가 반영된다`() {
        val userId = TestTenants.bootstrap(authService, tokenProvider, userRepository)
        val card =
            photoCardService.create(
                PhotoCardCreateRequest(
                    title = "재배열용",
                    memo = null,
                    tags = emptyList(),
                    photos = listOf(PhotoFile(url = "https://cdn/photo-cards/u$userId/d.jpg", originalName = "d.jpg", size = 1000)),
                    saleId = null,
                    customerId = null,
                ),
            )
        assertThat(quotaService.usage(userId).usedBytes).isEqualTo(1000)
        // reorder 창구로 사진 추가(또는 size 변경) — 사용량이 우회되지 않고 델타만큼 증가해야 한다.
        photoCardService.reorderPhotos(
            card.id,
            listOf(
                PhotoFile(url = "https://cdn/photo-cards/u$userId/d.jpg", originalName = "d.jpg", size = 1000),
                PhotoFile(url = "https://cdn/photo-cards/u$userId/e.jpg", originalName = "e.jpg", size = 2000),
            ),
        )
        assertThat(quotaService.usage(userId).usedBytes).isEqualTo(3000)
    }

    @Test
    fun `reorder도 사진 장수 제한을 적용한다`() {
        val userId = TestTenants.bootstrap(authService, tokenProvider, userRepository)
        val card =
            photoCardService.create(
                PhotoCardCreateRequest(
                    title = "장수제한용",
                    memo = null,
                    tags = emptyList(),
                    photos = emptyList(),
                    saleId = null,
                    customerId = null,
                ),
            )
        val tooMany = (1..11).map { PhotoFile(url = "https://cdn/photo-cards/u$userId/$it.jpg", originalName = "$it.jpg", size = 1) }
        assertThatThrownBy { photoCardService.reorderPhotos(card.id, tooMany) }
            .isInstanceOf(AppException::class.java)
    }
}
