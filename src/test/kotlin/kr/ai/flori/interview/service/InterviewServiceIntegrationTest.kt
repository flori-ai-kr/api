package kr.ai.flori.interview.service

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.common.error.AppException
import kr.ai.flori.interview.dto.InterviewApplyRequest
import kr.ai.flori.interview.error.InterviewErrorCode
import kr.ai.flori.interview.repository.InterviewRequestRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class InterviewServiceIntegrationTest {
    @Autowired private lateinit var service: InterviewService

    @Autowired private lateinit var repository: InterviewRequestRepository

    @BeforeEach
    fun setUp() {
        repository.deleteAll()
    }

    @Test
    fun `신청하면 저장되고 전화번호는 숫자만으로 정규화된다`() {
        val res = service.apply(InterviewApplyRequest(name = "홍길동", phone = "010-1234-5678"))
        assertThat(res.applied).isTrue()
        assertThat(repository.existsByPhone("01012345678")).isTrue()
    }

    @Test
    fun `같은 번호(하이픈 유무만 다름)로 재신청하면 ALREADY_APPLIED`() {
        service.apply(InterviewApplyRequest(name = "홍길동", phone = "010-1234-5678"))
        val ex =
            assertThrows<AppException> {
                service.apply(InterviewApplyRequest(name = "김철수", phone = "01012345678"))
            }
        assertThat(ex.errorCode).isEqualTo(InterviewErrorCode.ALREADY_APPLIED)
    }

    @Test
    fun `서로 다른 번호는 모두 저장된다`() {
        service.apply(InterviewApplyRequest(name = "A", phone = "010-1111-2222"))
        service.apply(InterviewApplyRequest(name = "B", phone = "010-3333-4444"))
        assertThat(repository.count()).isEqualTo(2L)
    }
}
