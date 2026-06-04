package kr.ai.flori.settings.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import kr.ai.flori.common.validation.FieldLimits

data class LabelSettingCreateRequest(
    @field:NotBlank(message = "이름(label)은 필수입니다")
    @field:Size(max = FieldLimits.LABEL, message = "이름이 너무 깁니다")
    val label: String?,
    @field:Size(max = FieldLimits.COLOR, message = "색상 값이 올바르지 않습니다")
    val color: String? = null,
    @field:Size(max = FieldLimits.VALUE, message = "값이 너무 깁니다")
    val value: String? = null,
)

data class LabelSettingUpdateRequest(
    @field:NotBlank(message = "이름(label)은 필수입니다")
    @field:Size(max = FieldLimits.LABEL, message = "이름이 너무 깁니다")
    val label: String?,
    @field:NotBlank(message = "색상은 필수입니다")
    @field:Size(max = FieldLimits.COLOR, message = "색상 값이 올바르지 않습니다")
    val color: String?,
)

data class LabelSettingResponse(
    val id: Long,
    val value: String,
    val label: String,
    val color: String,
    val sortOrder: Int,
)

data class UserPreferencesResponse(
    val bottomNavItems: List<String>,
)

data class UpdateBottomNavRequest(
    @field:NotNull(message = "항목은 필수입니다")
    @field:Size(min = 4, max = 6, message = "하단바 항목은 4~6개여야 합니다")
    val items: List<
        @Size(max = FieldLimits.VALUE, message = "항목 값이 올바르지 않습니다")
        String,
    >?,
)

data class PushSubscribeRequest(
    @field:NotBlank(message = "토큰(endpoint)은 필수입니다")
    @field:Size(max = FieldLimits.PUSH_ENDPOINT, message = "endpoint가 너무 깁니다")
    val endpoint: String?,
    @field:Size(max = FieldLimits.PUSH_KEY, message = "p256dh가 너무 깁니다")
    val p256dh: String? = null,
    @field:Size(max = FieldLimits.PUSH_KEY, message = "auth가 너무 깁니다")
    val auth: String? = null,
    @field:Size(max = FieldLimits.USER_AGENT, message = "userAgent가 너무 깁니다")
    val userAgent: String? = null,
)

data class PushStatusResponse(
    val subscribed: Boolean,
)

/** 테스트 알림 발송 결과 — 성공 발송 건수. */
data class PushTestResponse(
    val sent: Int,
)
