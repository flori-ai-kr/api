package kr.ai.flori.admin

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.admin.dto.PromptCreateRequest
import kr.ai.flori.admin.service.AdminPromptService
import kr.ai.flori.ai.repository.AiPromptRepository
import kr.ai.flori.ai.service.PromptResolver
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.support.TestAccounts
import kr.ai.flori.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.util.UUID

/**
 * AdminPromptService 통합테스트(SPEC-AI-008). 채널당 active 1개 불변식·active 삭제거부·모델 화이트리스트·
 * 캐시 무효화를 실제 Zonky PG에서 검증한다. PromptResolver만 mock해 invalidate 호출을 관측한다.
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class AdminPromptServiceTest {
    @Autowired lateinit var service: AdminPromptService

    @Autowired lateinit var repository: AiPromptRepository

    @Autowired lateinit var authService: AuthService

    @Autowired lateinit var tokenProvider: JwtTokenProvider

    @Autowired lateinit var userRepository: UserRepository

    @MockitoBean lateinit var promptResolver: PromptResolver

    @AfterEach fun tearDown() = TenantContext.clear()

    private fun adminContext() {
        val email = "adm-${UUID.randomUUID()}@flori.dev"
        TestAccounts.register(authService, tokenProvider, email)
        val userId = requireNotNull(userRepository.findByEmail(email)).id!!
        TenantContext.set(userId)
    }

    // ai_prompt는 user_id 없는 전역 테이블 — SpringBootTest 메서드 간 DB 공유라 버전을 고유화해 충돌 방지.
    private fun uniqueVersion(prefix: String) = "$prefix-${UUID.randomUUID()}"

    private fun create(
        prefix: String,
        activate: Boolean = false,
    ) = service.create(PromptCreateRequest(version = uniqueVersion(prefix), systemMd = "시스템 $prefix", activate = activate))

    @Test
    fun `활성화하면 같은 채널의 기존 active 가 비활성된다(채널당 1개 불변식)`() {
        adminContext()
        val v1 = create("v1", activate = true)
        val v2 = create("v2")

        service.activate(v2.id)

        assertThat(repository.findFirstByChannelAndIsActiveTrueAndDeletedAtIsNull("blog")!!.id).isEqualTo(v2.id)
        assertThat(repository.findByIdAndDeletedAtIsNull(v1.id)!!.isActive).isFalse()
    }

    @Test
    fun `active 프롬프트는 삭제할 수 없다`() {
        adminContext()
        val v1 = create("v1", activate = true)

        assertThatThrownBy { service.delete(v1.id) }.isInstanceOf(AppException::class.java)
    }

    @Test
    fun `화이트리스트 밖 모델은 거부한다`() {
        adminContext()

        assertThatThrownBy {
            service.create(PromptCreateRequest(version = "bad", systemMd = "x", model = "gpt-4o"))
        }.isInstanceOf(AppException::class.java)
    }

    @Test
    fun `활성화 시 캐시를 무효화한다`() {
        adminContext()
        val v1 = create("v1")

        service.activate(v1.id)

        Mockito.verify(promptResolver).invalidate("blog")
    }

    @Test
    fun `clone 은 원본 본문을 복제한다`() {
        adminContext()
        val src = service.create(PromptCreateRequest(version = uniqueVersion("v1"), systemMd = "원본시스템", rulesMd = "원본규칙"))

        val cloned = service.create(PromptCreateRequest(version = uniqueVersion("v2"), fromId = src.id))

        assertThat(cloned.systemMd).isEqualTo("원본시스템")
        assertThat(cloned.rulesMd).isEqualTo("원본규칙")
    }
}
