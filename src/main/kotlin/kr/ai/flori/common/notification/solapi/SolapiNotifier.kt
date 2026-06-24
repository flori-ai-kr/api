package kr.ai.flori.common.notification.solapi

import kr.ai.flori.common.notification.NotificationSendRecorder
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * SOLAPI 알림톡 발송 도구(재사용). 카카오 알림톡은 공식 중계사(SOLAPI) REST(/messages/v4/send)를
 * HMAC-SHA256 인증으로 호출한다. @Async(비차단) + best-effort(실패는 로깅만, 본 작업 비차단).
 * 미설정(자격/템플릿/발신번호 공백)이면 콘솔 폴백. 발송 시도분은 [NotificationSendRecorder]로 기록.
 */
@Component
class SolapiNotifier(
    private val properties: SolapiProperties,
    private val recorder: NotificationSendRecorder,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val restClient =
        RestClient
            .builder()
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(CONNECT_TIMEOUT)
                    setReadTimeout(READ_TIMEOUT)
                },
            ).build()

    /** 사업자 인증 접수(제출) → 점주에게 접수 알림톡. */
    @Async
    fun sendBusinessSubmitted(
        userId: Long,
        phoneNumber: String,
        storeName: String,
    ) = send(userId, phoneNumber, properties.submittedTemplateId, mapOf("#{상호}" to storeName), TITLE_SUBMITTED)

    /** 사업자 인증 승인 → 점주에게 승인 알림톡. */
    @Async
    fun sendBusinessApproved(
        userId: Long,
        phoneNumber: String,
        storeName: String,
    ) = send(userId, phoneNumber, properties.approvalTemplateId, mapOf("#{상호}" to storeName), TITLE_APPROVED)

    /** 사업자 인증 거절 → 점주에게 사유 포함 알림톡. */
    @Async
    fun sendBusinessRejected(
        userId: Long,
        phoneNumber: String,
        storeName: String,
        reason: String,
    ) = send(
        userId,
        phoneNumber,
        properties.rejectedTemplateId,
        mapOf("#{상호}" to storeName, "#{사유}" to reason),
        TITLE_REJECTED,
    )

    @Suppress("TooGenericExceptionCaught")
    private fun send(
        userId: Long,
        phoneNumber: String,
        templateId: String,
        variables: Map<String, String>,
        title: String,
    ) {
        val to = phoneNumber.filter { it.isDigit() }
        if (to.isBlank()) {
            log.warn("[Solapi] 발송 스킵 — 전화번호 없음(userProfile.phoneNumber 미백필?) title={}", title)
            return
        }
        if (!properties.hasCredentials() || templateId.isBlank()) {
            log.info("[Solapi] 미설정 — 콘솔 폴백: {} to={} keys={}", title, maskPhone(to), variables.keys)
            return
        }
        try {
            postAlimtalk(to, templateId, variables)
            recorder.record(SOURCE, TYPE, success = true, targetUserId = userId, title = title, errorMessage = null)
            log.info("[Solapi] {} 발송 to={}", title, maskPhone(to))
        } catch (e: Exception) {
            recorder.record(SOURCE, TYPE, success = false, targetUserId = userId, title = title, errorMessage = e.message)
            log.warn("[Solapi] 발송 실패(무시): {} {}", title, e.message)
        }
    }

    private fun postAlimtalk(
        to: String,
        templateId: String,
        variables: Map<String, String>,
    ) {
        val date = Instant.now().toString()
        val salt = UUID.randomUUID().toString().replace("-", "")
        val signature = hmacSha256Hex(properties.apiSecret, date + salt)
        val auth = "HMAC-SHA256 apiKey=${properties.apiKey}, date=$date, salt=$salt, signature=$signature"
        val body =
            mapOf(
                "message" to
                    mapOf(
                        "to" to to,
                        "from" to properties.senderPhone.filter { it.isDigit() },
                        "kakaoOptions" to
                            mapOf(
                                "pfId" to properties.pfId,
                                "templateId" to templateId,
                                "variables" to variables,
                                "disableSms" to false,
                            ),
                    ),
            )
        restClient
            .post()
            .uri("${properties.baseUrl}/messages/v4/send")
            .header("Authorization", auth)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .toBodilessEntity()
    }

    private fun hmacSha256Hex(
        secret: String,
        data: String,
    ): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun maskPhone(phone: String): String =
        if (phone.length >= PHONE_VISIBLE_TAIL) {
            "*".repeat(phone.length - PHONE_VISIBLE_TAIL) + phone.takeLast(PHONE_VISIBLE_TAIL)
        } else {
            "****"
        }

    private companion object {
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(5)
        val READ_TIMEOUT: Duration = Duration.ofSeconds(10)
        const val PHONE_VISIBLE_TAIL = 4
        const val SOURCE = "alimtalk"
        const val TYPE = "business_verification"
        const val TITLE_SUBMITTED = "사업자 인증 접수"
        const val TITLE_APPROVED = "사업자 인증 승인"
        const val TITLE_REJECTED = "사업자 인증 거절"
    }
}
