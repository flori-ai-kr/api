package kr.ai.flori.common.notification.solapi

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * SOLAPI(메시지 발송 대행사) 설정. 실제 값은 환경변수(${ENV})에서만 해결(시크릿 금지).
 * 알림톡 발송에는 pfId(발신프로필)+templateId(승인 템플릿)가 필요하며, 카카오 비즈채널
 * 사업자 인증이 끝나야 발급된다. senderPhone은 SMS 폴백 발신번호.
 * 값이 비어 있으면 SolapiNotifier가 콘솔 폴백으로 동작한다(로컬·미설정 부팅 가능).
 */
@ConfigurationProperties(prefix = "solapi")
data class SolapiProperties(
    val apiKey: String = "",
    val apiSecret: String = "",
    /** 등록된 SMS 발신번호 — 알림톡 실패 시 폴백 + 발신자 표기. */
    val senderPhone: String = "",
    /** 카카오 알림톡 발신프로필 ID (비즈채널 인증 후 발급). */
    val pfId: String = "",
    /** 사업자 인증 승인 알림톡 템플릿 ID. */
    val approvalTemplateId: String = "",
    /** 사업자 인증 접수(제출) 알림톡 템플릿 ID. */
    val submittedTemplateId: String = "",
    /** 사업자 인증 거절(반려) 알림톡 템플릿 ID. */
    val rejectedTemplateId: String = "",
    val baseUrl: String = "https://api.solapi.com",
) {
    /** 발송 공통 자격(키/시크릿/발신프로필/발신번호)이 모두 설정됐는지. 템플릿ID는 발송별로 따로 검사. */
    fun hasCredentials(): Boolean =
        apiKey.isNotBlank() &&
            apiSecret.isNotBlank() &&
            pfId.isNotBlank() &&
            senderPhone.isNotBlank()
}
