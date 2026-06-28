package kr.ai.flori.common.push

import kr.ai.flori.common.notification.NotificationSendRecorder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate

/**
 * 검증:
 * - 딥링크: web 구독엔 webUrl, 모바일(FCM) 구독엔 type/id(data)를 전달.
 * - 발송 로깅: 발송 1건당 notification_send_logs 기록(채널·타입·성공·대상·에러사유). 브로드캐스트는 자체 집계행을 쓰므로 스킵.
 */
class PushDispatcherTest {
    private class RecordingFcm(
        private val result: PushResult = PushResult(success = true),
    ) : PushService {
        val sent = mutableListOf<PushMessage>()

        override fun send(message: PushMessage): PushResult {
            sent += message
            return result
        }
    }

    private class RecordingWeb(
        private val result: WebPushResult = WebPushResult(success = true),
    ) : WebPushSender {
        val sent = mutableListOf<WebPushPayload>()

        override fun send(
            target: WebPushTarget,
            payload: WebPushPayload,
        ): WebPushResult {
            sent += payload
            return result
        }
    }

    private class RecordingRecorder : NotificationSendRecorder {
        data class Rec(
            val source: String,
            val type: String,
            val success: Boolean,
            val targetUserId: Long?,
            val title: String?,
            val errorMessage: String?,
        )

        val recs = mutableListOf<Rec>()

        override fun record(
            source: String,
            type: String,
            success: Boolean,
            targetUserId: Long?,
            title: String?,
            errorMessage: String?,
        ) {
            recs += Rec(source, type, success, targetUserId, title, errorMessage)
        }
    }

    /** push_subscriptions 한 행 + 수신설정 빈 결과(기본 켜짐). 구독 비활성화 UPDATE 는 no-op. */
    private fun fakeJdbc(row: Map<String, Any>) =
        object : JdbcTemplate() {
            override fun queryForList(
                sql: String,
                vararg args: Any?,
            ): MutableList<MutableMap<String, Any>> = mutableListOf(LinkedHashMap(row))

            override fun <T> queryForList(
                sql: String,
                elementType: Class<T>,
                vararg args: Any?,
            ): MutableList<T> = mutableListOf()

            override fun update(
                sql: String,
                vararg args: Any?,
            ): Int = 0
        }

    private fun webRow() = mapOf("endpoint" to "https://push/ep", "p256dh" to "p", "auth" to "a")

    private fun fcmRow() = mapOf("endpoint" to "fcm-token", "p256dh" to "", "auth" to "")

    @Test
    fun `FCM 경로 - 딥링크 type id 를 data 에 싣고 fcm 채널로 로깅`() {
        val fcm = RecordingFcm()
        val web = RecordingWeb()
        val rec = RecordingRecorder()

        PushDispatcher(fcm, web, fakeJdbc(fcmRow()), rec)
            .sendToUser(1L, "제목", "본문", PushLinks.community(42), PushTypes.COMMUNITY_NOTICE)

        assertThat(fcm.sent).hasSize(1)
        assertThat(fcm.sent[0].data).isEqualTo(mapOf("type" to "community", "id" to "42"))
        assertThat(rec.recs).hasSize(1)
        assertThat(rec.recs[0]).isEqualTo(
            RecordingRecorder.Rec("fcm", PushTypes.COMMUNITY_NOTICE, true, 1L, "제목", null),
        )
    }

    @Test
    fun `web 경로 - 딥링크 webUrl 전달 + web_push 채널로 로깅`() {
        val fcm = RecordingFcm()
        val web = RecordingWeb()
        val rec = RecordingRecorder()

        PushDispatcher(fcm, web, fakeJdbc(webRow()), rec)
            .sendToUser(1L, "제목", "본문", PushLinks.community(42), PushTypes.COMMUNITY_NOTICE)

        assertThat(web.sent).hasSize(1)
        assertThat(web.sent[0].url).isEqualTo("/admin/community/42")
        assertThat(rec.recs).hasSize(1)
        assertThat(rec.recs[0].source).isEqualTo("web_push")
        assertThat(rec.recs[0].type).isEqualTo(PushTypes.COMMUNITY_NOTICE)
    }

    @Test
    fun `영구 실패(구독 만료) - errorMessage 구독 만료로 기록`() {
        val rec = RecordingRecorder()
        // FCM tokenInvalid=true → gone
        val dispatcher =
            PushDispatcher(RecordingFcm(PushResult(success = false, tokenInvalid = true)), RecordingWeb(), fakeJdbc(fcmRow()), rec)

        dispatcher.sendToUser(1L, "제목", "본문", null, PushTypes.COMMUNITY_NOTICE)

        assertThat(rec.recs).hasSize(1)
        assertThat(rec.recs[0].success).isFalse()
        assertThat(rec.recs[0].errorMessage).isEqualTo("구독 만료")
    }

    @Test
    fun `일시 실패 - errorMessage 발송 실패로 기록`() {
        val rec = RecordingRecorder()
        // web success=false, gone=false → 일시 실패
        val dispatcher =
            PushDispatcher(RecordingFcm(), RecordingWeb(WebPushResult(success = false, subscriptionGone = false)), fakeJdbc(webRow()), rec)

        dispatcher.sendToUser(1L, "제목", "본문", null, PushTypes.COMMUNITY_NOTICE)

        assertThat(rec.recs).hasSize(1)
        assertThat(rec.recs[0].success).isFalse()
        assertThat(rec.recs[0].errorMessage).isEqualTo("발송 실패")
    }

    @Test
    fun `브로드캐스트 - 자체 집계행을 쓰므로 디스패처는 로깅하지 않는다`() {
        val rec = RecordingRecorder()

        PushDispatcher(RecordingFcm(), RecordingWeb(), fakeJdbc(webRow()), rec)
            .sendToUser(1L, "제목", "본문", null, PushTypes.BROADCAST)

        assertThat(rec.recs).isEmpty()
    }
}
