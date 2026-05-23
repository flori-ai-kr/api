package com.hazel.common.push

import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory

/**
 * FCM 발송 구현. 영구 실패(UNREGISTERED/INVALID_ARGUMENT)는 tokenInvalid로 표시해
 * 호출측이 해당 푸시 구독을 비활성화하도록 한다.
 */
class FirebasePushService(
    firebaseApp: FirebaseApp,
) : PushService {
    private val log = LoggerFactory.getLogger(javaClass)
    private val messaging = FirebaseMessaging.getInstance(firebaseApp)

    override fun send(message: PushMessage): PushResult {
        val fcmMessage =
            Message
                .builder()
                .setToken(message.token)
                .setNotification(
                    Notification
                        .builder()
                        .setTitle(message.title)
                        .setBody(message.body)
                        .build(),
                ).putAllData(message.data)
                .build()
        return try {
            messaging.send(fcmMessage)
            PushResult(success = true)
        } catch (ex: FirebaseMessagingException) {
            val invalid = ex.messagingErrorCode in PERMANENT_FAILURES
            log.warn("FCM 전송 실패 code={} invalidToken={}", ex.messagingErrorCode, invalid)
            PushResult(success = false, tokenInvalid = invalid)
        }
    }

    private companion object {
        val PERMANENT_FAILURES = setOf(MessagingErrorCode.UNREGISTERED, MessagingErrorCode.INVALID_ARGUMENT)
    }
}
