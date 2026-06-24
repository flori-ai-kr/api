package kr.ai.flori.common.notification.solapi

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.admin.entity.NotificationSendLog
import kr.ai.flori.admin.repository.NotificationSendLogRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * 자격은 설정하되 base-url을 연결 거부되는 호스트로 두어 발송을 강제 실패시키고,
 * recorder가 failed 행을 적재하는지 검증. (@Async라 폴링으로 대기)
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest(
    properties = [
        "solapi.api-key=test-key",
        "solapi.api-secret=test-secret",
        "solapi.sender-phone=01000000000",
        "solapi.pf-id=KA01PFtest",
        "solapi.approval-template-id=KA01TPtest",
        "solapi.base-url=http://localhost:1",
    ],
)
class SolapiNotifierTest {
    @Autowired private lateinit var notifier: SolapiNotifier

    @Autowired private lateinit var repository: NotificationSendLogRepository

    private fun awaitLog(): NotificationSendLog? {
        val deadline = System.currentTimeMillis() + 4000
        while (System.currentTimeMillis() < deadline) {
            val row = repository.findAll().firstOrNull { it.source == "alimtalk" && it.targetUserId == 7L }
            if (row != null) return row
            Thread.sleep(50)
        }
        return null
    }

    @Test
    fun `발송 실패 시 failed 로그가 적재된다`() {
        notifier.sendBusinessApproved(7L, "01012345678", "플로리 꽃집")

        val row = awaitLog()
        assertThat(row).isNotNull
        assertThat(row!!.type).isEqualTo("business_verification")
        assertThat(row.status).isEqualTo("failed")
        assertThat(row.targetUserId).isEqualTo(7L)
        assertThat(row.title).isEqualTo("사업자 인증 승인")
    }

    @Test
    fun `전화번호가 없으면 발송도 기록도 하지 않는다`() {
        notifier.sendBusinessApproved(8L, "", "플로리 꽃집")

        Thread.sleep(500)
        assertThat(repository.findAll().filter { it.targetUserId == 8L }).isEmpty()
    }
}
