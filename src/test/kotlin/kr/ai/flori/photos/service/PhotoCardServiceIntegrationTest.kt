package kr.ai.flori.photos.service

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.storage.S3PresignService
import kr.ai.flori.common.storage.StorageProperties
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.photos.dto.FileMetaRequest
import kr.ai.flori.photos.dto.PhotoCardCreateRequest
import kr.ai.flori.photos.dto.PhotoCardUpdateRequest
import kr.ai.flori.photos.entity.PhotoFile
import kr.ai.flori.photos.repository.PhotoCardRepository
import kr.ai.flori.support.TestAccounts
import kr.ai.flori.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.util.UUID

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class PhotoCardServiceIntegrationTest {
    @Autowired
    lateinit var photoCardService: PhotoCardService

    @Autowired
    lateinit var photoCardRepository: PhotoCardRepository

    @Autowired
    lateinit var jdbcTemplate: org.springframework.jdbc.core.JdbcTemplate

    @Autowired
    lateinit var saleRepository: kr.ai.flori.sales.repository.SaleRepository

    @Autowired
    lateinit var customerRepository: kr.ai.flori.customers.repository.CustomerRepository

    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var tokenProvider: JwtTokenProvider

    @Autowired
    lateinit var userRepository: UserRepository

    // 자격증명이 있는 presigner로 업로드 타깃 성공경로 검증(로컬 서명, 네트워크 불요)
    private val presignService =
        S3PresignService(
            S3Presigner
                .builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("AKIA", "secret")))
                .build(),
            S3Client
                .builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("AKIA", "secret")))
                .build(),
            StorageProperties(
                region = "ap-northeast-2",
                s3 = StorageProperties.S3(bucket = "flori-test"),
                cloudfront = StorageProperties.CloudFront("cdn.flori.dev"),
            ),
        )
    private val signingService by lazy { PhotoCardService(photoCardRepository, presignService, saleRepository, customerRepository) }

    @AfterEach
    fun tearDown() = TenantContext.clear()

    private fun newTenant(): Long {
        val email = "photo-${UUID.randomUUID()}@flori.dev"
        TestAccounts.register(authService, tokenProvider, email)
        val userId = requireNotNull(userRepository.findByEmail(email)).id!!
        TenantContext.set(userId)
        return userId
    }

    private fun card(tags: List<String> = emptyList()) = photoCardService.create(PhotoCardCreateRequest(title = "결혼식 부케", tags = tags))

    @Test
    fun `생성·조회·목록(커서)이 동작한다`() {
        newTenant()
        card()
        card()
        val page = photoCardService.list(null, null, null)
        assertThat(page.cards).hasSize(2)
        assertThat(page.hasMore).isFalse()
    }

    @Test
    fun `커서 페이지네이션이 동일 updated_at 다수여도 전진하며 전부 순회한다`() {
        val userId = newTenant()
        repeat(20) { card() }
        // 모든 카드 updated_at 동일 강제 — id 보조키 없으면 키셋이 깨져 무한루프 나던 케이스 재현
        jdbcTemplate.update("UPDATE photo_cards SET updated_at = '2026-06-09T15:16:00Z' WHERE user_id = ?", userId)

        val seen = mutableSetOf<Long>()
        var cursor: String? = null
        var hasMore = true
        var guard = 0
        while (hasMore) {
            val page = photoCardService.list(null, cursor, null)
            page.cards.forEach { seen.add(it.id) }
            cursor = page.nextCursor
            hasMore = page.hasMore
            check(guard++ < 100) { "무한 페이지네이션 — 커서가 전진하지 않음" }
        }
        assertThat(seen).hasSize(20)
    }

    @Test
    fun `태그 필터로 조회한다`() {
        newTenant()
        card(tags = listOf("행사"))
        card(tags = listOf("개인"))
        assertThat(photoCardService.list("행사", null, null).cards).hasSize(1)
    }

    @Test
    fun `목록 응답에 총 카드수·총 사진장수가 필터 기준으로 집계된다`() {
        newTenant()
        photoCardService.create(
            PhotoCardCreateRequest(title = "행사용", tags = listOf("행사"), photos = listOf(PhotoFile("u1", "a.jpg"), PhotoFile("u2", "b.jpg"))),
        )
        photoCardService.create(
            PhotoCardCreateRequest(title = "개인용", tags = listOf("개인"), photos = listOf(PhotoFile("u3", "c.jpg"))),
        )

        val all = photoCardService.list(null, null, null)
        assertThat(all.totalCards).isEqualTo(2)
        assertThat(all.totalPhotos).isEqualTo(3)

        // 필터(tag) 적용 시 총계도 그 필터 기준이어야 한다.
        val filtered = photoCardService.list("행사", null, null)
        assertThat(filtered.totalCards).isEqualTo(1)
        assertThat(filtered.totalPhotos).isEqualTo(2)
    }

    @Test
    fun `사진 순서변경·1장 삭제가 동작한다`() {
        newTenant()
        val c =
            photoCardService.update(
                card().id,
                PhotoCardUpdateRequest(
                    photos = listOf(PhotoFile("u1", "a.jpg"), PhotoFile("u2", "b.jpg")),
                ),
            )
        assertThat(c.photos).hasSize(2)
        val reordered = photoCardService.reorderPhotos(c.id, listOf(PhotoFile("u2", "b.jpg"), PhotoFile("u1", "a.jpg")))
        assertThat(reordered.photos.first().url).isEqualTo("u2")
        assertThat(photoCardService.deletePhoto(c.id, "u2").photos).hasSize(1)
    }

    @Test
    fun `사진 11장 이상은 거부된다`() {
        newTenant()
        val photos = (1..11).map { PhotoFile("u$it", "$it.jpg") }
        assertThatThrownBy { photoCardService.create(PhotoCardCreateRequest(title = "x", photos = photos)) }
            .isInstanceOf(AppException::class.java)
    }

    @Test
    fun `업로드 타깃은 이미지가 아니면 거부된다`() {
        newTenant()
        val c = card()
        assertThatThrownBy {
            photoCardService.createUploadTargets(c.id, listOf(FileMetaRequest("a.pdf", "application/pdf", 100)))
        }.isInstanceOf(AppException::class.java)
    }

    @Test
    fun `업로드 타깃은 presigned PUT URL과 파일 URL을 발급한다`() {
        newTenant()
        val c = card()
        val targets = signingService.createUploadTargets(c.id, listOf(FileMetaRequest("flower.jpg", "image/jpeg", 1_000)))
        assertThat(targets).hasSize(1)
        assertThat(targets.first().uploadUrl).contains("flori-test").contains("X-Amz-Signature")
        assertThat(targets.first().fileUrl).startsWith("https://cdn.flori.dev/photo-cards/${c.id}/")
        assertThat(targets.first().originalName).isEqualTo("flower.jpg")
    }

    @Test
    fun `다른 테넌트의 사진 카드는 조회할 수 없다`() {
        newTenant()
        val mine = card()
        newTenant()
        assertThatThrownBy { photoCardService.get(mine.id) }.isInstanceOf(AppException::class.java)
        assertThat(photoCardService.list(null, null, null).cards).isEmpty()
    }
}
