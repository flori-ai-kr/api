package kr.ai.flori.admin.service

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * notification_send_logs.source CHECK 가 푸시 채널값('web_push'·'fcm')을 허용하는지 회귀 가드.
 * (CHECK 미확장 시 PushDispatcher 의 발송 로깅이 insert 실패로 조용히 0건이 되는 사고 방지)
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class NotificationSendLogServiceTest {
    @Autowired
    lateinit var service: NotificationSendLogService

    @Test
    fun `web_push fcm 채널 발송 로그를 적재할 수 있다(source CHECK 허용)`() {
        service.record(source = "web_push", type = "community_notice", sentCount = 1, failedCount = 0, targetUserId = 1L, title = "공지")
        service.record(source = "fcm", type = "reservation_reminder", sentCount = 0, failedCount = 1, targetUserId = 2L, title = "리마인더")

        val webPush = service.list(type = null, source = "web_push", status = null, page = 0, size = 20)
        val fcm = service.list(type = null, source = "fcm", status = null, page = 0, size = 20)

        assertThat(webPush).hasSize(1)
        assertThat(webPush[0].source).isEqualTo("web_push")
        assertThat(webPush[0].status).isEqualTo("sent")
        assertThat(fcm).hasSize(1)
        assertThat(fcm[0].source).isEqualTo("fcm")
        assertThat(fcm[0].status).isEqualTo("failed")
    }
}
