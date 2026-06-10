package kr.ai.flori.waitlist.service

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.common.error.AppException
import kr.ai.flori.waitlist.dto.WaitlistRegisterRequest
import kr.ai.flori.waitlist.error.WaitlistErrorCode
import kr.ai.flori.waitlist.repository.WaitlistRegistrationRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class WaitlistServiceIntegrationTest {
    @Autowired private lateinit var service: WaitlistService

    @Autowired private lateinit var repository: WaitlistRegistrationRepository

    @BeforeEach
    fun setUp() {
        repository.deleteAll()
    }

    @Test
    fun `등록하면 카운트가 증가하고 이메일은 정규화(소문자)되어 저장된다`() {
        val res = service.register(WaitlistRegisterRequest(email = "Hazel@Flori.AI.kr", shopName = "헤이즐"))
        assertThat(res.count).isEqualTo(1L)
        assertThat(res.closed).isFalse()
        assertThat(repository.existsByEmail("hazel@flori.ai.kr")).isTrue()
    }

    @Test
    fun `같은 이메일(대소문자만 다름)로 재등록하면 ALREADY_REGISTERED`() {
        service.register(WaitlistRegisterRequest(email = "dup@flori.ai.kr", shopName = "A"))
        val ex =
            assertThrows<AppException> {
                service.register(WaitlistRegisterRequest(email = "DUP@flori.ai.kr", shopName = "B"))
            }
        assertThat(ex.errorCode).isEqualTo(WaitlistErrorCode.ALREADY_REGISTERED)
    }

    @Test
    fun `여러 건 등록해도 정원 미만이면 closed=false`() {
        service.register(WaitlistRegisterRequest(email = "a@flori.ai.kr", shopName = "A"))
        val res = service.register(WaitlistRegisterRequest(email = "b@flori.ai.kr", shopName = "B"))
        assertThat(res.closed).isFalse()
        assertThat(res.count).isEqualTo(2L)
    }
}
