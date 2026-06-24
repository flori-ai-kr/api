package kr.ai.flori.admin.service

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.admin.repository.NotificationSendLogRepository
import kr.ai.flori.common.notification.NotificationSendRecorder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class NotificationSendLogRecorderAdapterTest {
    @Autowired private lateinit var recorder: NotificationSendRecorder

    @Autowired private lateinit var repository: NotificationSendLogRepository

    @Test
    fun `성공 기록은 sent로, 실패 기록은 failed로 적재된다`() {
        recorder.record("alimtalk", "business_verification", true, 1L, "사업자 인증 승인", null)
        recorder.record("alimtalk", "business_verification", false, 2L, "사업자 인증 접수", "boom")

        val all = repository.findAll().filter { it.type == "business_verification" }
        assertThat(all).hasSize(2)
        val ok = all.first { it.targetUserId == 1L }
        assertThat(ok.source).isEqualTo("alimtalk")
        assertThat(ok.type).isEqualTo("business_verification")
        assertThat(ok.status).isEqualTo("sent")
        assertThat(ok.sentCount).isEqualTo(1)
        val failed = all.first { it.targetUserId == 2L }
        assertThat(failed.source).isEqualTo("alimtalk")
        assertThat(failed.status).isEqualTo("failed")
        assertThat(failed.errorMessage).isEqualTo("boom")
    }
}
