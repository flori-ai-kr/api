package kr.ai.flori.photos.service

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.repository.UserRepository
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.ErrorCode
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.photos.dto.PhotoCardCreateRequest
import kr.ai.flori.photos.repository.PhotoCardRepository
import kr.ai.flori.support.TestAccounts
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.UUID

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class PhotoTagServiceIntegrationTest {
    @Autowired
    lateinit var tagService: PhotoTagService

    @Autowired
    lateinit var photoCardService: PhotoCardService

    @Autowired
    lateinit var photoCardRepository: PhotoCardRepository

    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var tokenProvider: JwtTokenProvider

    @Autowired
    lateinit var userRepository: UserRepository

    @AfterEach
    fun tearDown() = TenantContext.clear()

    private fun newTenant(): Long {
        val email = "ptag-${UUID.randomUUID()}@flori.dev"
        TestAccounts.register(authService, tokenProvider, email)
        val userId = requireNotNull(userRepository.findByEmail(email)).id!!
        TenantContext.set(userId)
        return userId
    }

    @Test
    fun `생성 시 색상 미지정이면 랜덤 색상이 부여된다`() {
        newTenant()
        val tag = tagService.create("행사", null)
        assertThat(tag.color).startsWith("#")
        assertThat(tagService.list()).hasSize(1)
    }

    @Test
    fun `중복 태그는 DUPLICATE`() {
        newTenant()
        tagService.create("행사", "#ff0000")
        assertThatThrownBy { tagService.create("행사", "#00ff00") }
            .isInstanceOfSatisfying(AppException::class.java) {
                assertThat(it.errorCode).isEqualTo(ErrorCode.DUPLICATE)
            }
    }

    @Test
    fun `태그 삭제 시 카드에서도 제거된다`() {
        newTenant()
        val tag = tagService.create("행사", "#ff0000")
        val cardId = photoCardService.create(PhotoCardCreateRequest(title = "카드", tags = listOf("행사", "개인"))).id

        tagService.delete(tag.id)

        val card = requireNotNull(photoCardRepository.findById(cardId).orElse(null))
        assertThat(card.tags).containsExactly("개인")
    }

    @Test
    fun `다른 테넌트의 태그는 삭제할 수 없다`() {
        newTenant()
        val tag = tagService.create("행사", "#ff0000")
        newTenant()
        assertThatThrownBy { tagService.delete(tag.id) }.isInstanceOf(AppException::class.java)
    }
}
