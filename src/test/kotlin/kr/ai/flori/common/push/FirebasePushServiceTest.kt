package kr.ai.flori.common.push

import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito

/**
 * FCM 발송 어댑터 단위테스트. FCM SDK는 정적 팩토리([FirebaseMessaging.getInstance])로만 접근되므로
 * [Mockito.mockStatic]으로 메시징 인스턴스를 주입하고, 영구 실패 시 토큰 무효화 분기를 검증한다.
 */
class FirebasePushServiceTest {
    private val app: FirebaseApp = Mockito.mock(FirebaseApp::class.java)

    /** mockStatic로 getInstance를 스텁한 상태에서 서비스를 만들고 [block]을 실행한다. */
    private fun withMessaging(block: (FirebasePushService, FirebaseMessaging) -> Unit) {
        val messaging = Mockito.mock(FirebaseMessaging::class.java)
        Mockito.mockStatic(FirebaseMessaging::class.java).use { statics ->
            statics.`when`<FirebaseMessaging> { FirebaseMessaging.getInstance(app) }.thenReturn(messaging)
            val service = FirebasePushService(app)
            block(service, messaging)
        }
    }

    private fun messagingError(code: MessagingErrorCode?): FirebaseMessagingException =
        Mockito.mock(FirebaseMessagingException::class.java).also {
            Mockito.`when`(it.messagingErrorCode).thenReturn(code)
        }

    @Test
    fun `전송 성공이면 success true`() {
        withMessaging { service, messaging ->
            Mockito.`when`(messaging.send(any(Message::class.java))).thenReturn("projects/x/messages/1")

            val result = service.send(PushMessage(token = "tok", title = "제목", body = "본문", data = mapOf("k" to "v")))

            assertThat(result.success).isTrue()
            assertThat(result.tokenInvalid).isFalse()
        }
    }

    @Test
    fun `UNREGISTERED 실패는 토큰 무효로 표시`() {
        withMessaging { service, messaging ->
            val ex = messagingError(MessagingErrorCode.UNREGISTERED)
            Mockito.`when`(messaging.send(any(Message::class.java))).thenThrow(ex)

            val result = service.send(PushMessage(token = "dead", title = "t", body = "b"))

            assertThat(result.success).isFalse()
            assertThat(result.tokenInvalid).isTrue()
        }
    }

    @Test
    fun `INVALID_ARGUMENT 실패도 토큰 무효로 표시`() {
        withMessaging { service, messaging ->
            val ex = messagingError(MessagingErrorCode.INVALID_ARGUMENT)
            Mockito.`when`(messaging.send(any(Message::class.java))).thenThrow(ex)

            val result = service.send(PushMessage(token = "bad", title = "t", body = "b"))

            assertThat(result.success).isFalse()
            assertThat(result.tokenInvalid).isTrue()
        }
    }

    @Test
    fun `일시적 실패(INTERNAL)는 토큰을 무효로 보지 않는다`() {
        withMessaging { service, messaging ->
            val ex = messagingError(MessagingErrorCode.INTERNAL)
            Mockito.`when`(messaging.send(any(Message::class.java))).thenThrow(ex)

            val result = service.send(PushMessage(token = "tok", title = "t", body = "b"))

            assertThat(result.success).isFalse()
            assertThat(result.tokenInvalid).isFalse()
        }
    }
}
