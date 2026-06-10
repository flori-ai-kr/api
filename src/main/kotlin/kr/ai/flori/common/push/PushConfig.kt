package kr.ai.flori.common.push

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.io.InputStream

/**
 * 푸시 빈 구성.
 * - fcm.enabled=true 이면 FirebaseApp 초기화 + FCM 구현 사용.
 * - 그 외(로컬/테스트)에는 로깅 폴백을 사용해 컨텍스트가 항상 부팅된다.
 */
@Configuration
class PushConfig {
    @Bean
    @ConditionalOnProperty(prefix = "fcm", name = ["enabled"], havingValue = "true")
    fun firebaseApp(
        @Value("\${fcm.credentials:}") credentials: String,
    ): FirebaseApp {
        val options =
            FirebaseOptions
                .builder()
                .setCredentials(GoogleCredentials.fromStream(openCredentials(credentials)))
                .build()
        return if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options)
        } else {
            FirebaseApp.getInstance()
        }
    }

    @Bean
    @ConditionalOnBean(FirebaseApp::class)
    fun firebasePushService(firebaseApp: FirebaseApp): PushService = FirebasePushService(firebaseApp)

    @Bean
    @ConditionalOnMissingBean(PushService::class)
    fun loggingPushService(): PushService = LoggingPushService()

    /**
     * Web Push(VAPID) 발송기. public/private 키가 모두 설정되면 실제 VAPID 발송,
     * 아니면 로깅 폴백(로컬/테스트에서 컨텍스트가 항상 부팅되도록).
     */
    @Bean
    fun webPushSender(
        @Value("\${vapid.public-key:}") publicKey: String,
        @Value("\${vapid.private-key:}") privateKey: String,
        @Value("\${vapid.subject:mailto:admin@flori.ai.kr}") subject: String,
        objectMapper: ObjectMapper,
    ): WebPushSender =
        if (publicKey.isNotBlank() && privateKey.isNotBlank()) {
            VapidWebPushSender(publicKey, privateKey, subject, objectMapper)
        } else {
            LoggingWebPushSender()
        }

    /** credentials가 JSON 본문이면 그대로, 아니면 파일 경로로 해석. */
    private fun openCredentials(credentials: String): InputStream =
        if (credentials.trimStart().startsWith("{")) {
            ByteArrayInputStream(credentials.toByteArray())
        } else {
            FileInputStream(credentials)
        }
}
