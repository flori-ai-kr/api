package com.hazel.photos.service

import com.hazel.auth.dto.SignupRequest
import com.hazel.auth.repository.UserRepository
import com.hazel.auth.service.AuthService
import com.hazel.common.error.AppException
import com.hazel.common.storage.S3PresignService
import com.hazel.common.storage.StorageProperties
import com.hazel.common.tenant.TenantContext
import com.hazel.photos.dto.FileMetaRequest
import com.hazel.photos.dto.PhotoCardCreateRequest
import com.hazel.photos.dto.PhotoCardUpdateRequest
import com.hazel.photos.entity.PhotoFile
import com.hazel.photos.repository.PhotoCardRepository
import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
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
    lateinit var saleRepository: com.hazel.sales.repository.SaleRepository

    @Autowired
    lateinit var authService: AuthService

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
            StorageProperties(
                region = "ap-northeast-2",
                s3 = StorageProperties.S3(bucket = "hazel-test"),
                cloudfront = StorageProperties.CloudFront("cdn.hazel.dev"),
            ),
        )
    private val signingService by lazy { PhotoCardService(photoCardRepository, presignService, saleRepository) }

    @AfterEach
    fun tearDown() = TenantContext.clear()

    private fun newTenant(): UUID {
        val email = "photo-${UUID.randomUUID()}@hazel.dev"
        authService.signup(SignupRequest(email, "password123", null))
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
    fun `태그 필터로 조회한다`() {
        newTenant()
        card(tags = listOf("행사"))
        card(tags = listOf("개인"))
        assertThat(photoCardService.list("행사", null, null).cards).hasSize(1)
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
        assertThat(targets.first().uploadUrl).contains("hazel-test").contains("X-Amz-Signature")
        assertThat(targets.first().fileUrl).startsWith("https://cdn.hazel.dev/photo-cards/${c.id}/")
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
