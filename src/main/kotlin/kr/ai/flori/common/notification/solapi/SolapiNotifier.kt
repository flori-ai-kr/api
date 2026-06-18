package kr.ai.flori.common.notification.solapi

import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Instant
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * SOLAPI 알림톡 발송 도구(재사용). 카카오 알림톡은 카카오가 직접 API를 주지 않으므로
 * 공식 중계사(SOLAPI) REST(/messages/v4/send)를 HMAC-SHA256 인증으로 호출한다.
 * - @Async: 응답/리스너 스레드 비차단.
 * - 미설정(키/pfId/템플릿/발신번호 공백)이면 콘솔 폴백(로컬·인증 전 부팅 가능).
 * - best-effort: 전송 실패는 로깅만(본 작업을 막지 않음).
 * - disableSms=false: 알림톡 실패 시 동일 내용 SMS로 자동 폴백.
 */
@Component
class SolapiNotifier(
    private val properties: SolapiProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val restClient = RestClient.create()

    /** 사업자 인증 승인 → 점주에게 승인 알림톡 발송. */
    @Async
    @Suppress("TooGenericExceptionCaught")
    fun sendBusinessApproved(
        phoneNumber: String,
        storeName: String,
    ) {
        val to = phoneNumber.filter { it.isDigit() }
        if (to.isBlank()) {
            log.warn("[Solapi] 발송 스킵 — 전화번호 없음(userProfile.phoneNumber 미백필?)")
            return
        }
        if (!properties.isConfigured()) {
            log.info("[Solapi] 미설정 — 콘솔 폴백: 승인 알림톡 to={} 상호={}", to, storeName)
            return
        }
        try {
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
                                    "templateId" to properties.approvalTemplateId,
                                    "variables" to mapOf("#{상호}" to storeName),
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
            log.info("[Solapi] 승인 알림톡 발송 to={}", to)
        } catch (e: Exception) {
            log.warn("[Solapi] 발송 실패(무시): {}", e.message)
        }
    }

    private fun hmacSha256Hex(
        secret: String,
        data: String,
    ): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
