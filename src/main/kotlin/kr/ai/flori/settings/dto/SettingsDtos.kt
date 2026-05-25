package kr.ai.flori.settings.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.util.UUID

data class LabelSettingCreateRequest(
    @field:NotBlank(message = "이름(label)은 필수입니다")
    val label: String?,
    val color: String? = null,
    val value: String? = null,
)

data class LabelSettingUpdateRequest(
    @field:NotBlank(message = "이름(label)은 필수입니다")
    val label: String?,
    @field:NotBlank(message = "색상은 필수입니다")
    val color: String?,
)

data class LabelSettingResponse(
    val id: UUID,
    val value: String,
    val label: String,
    val color: String,
    val sortOrder: Int,
)

data class CardCompanyCreateRequest(
    @field:NotBlank(message = "카드사명은 필수입니다")
    val name: String?,
    val feeRate: BigDecimal = BigDecimal("2.0"),
    val depositDays: Int = 3,
)

data class CardCompanyUpdateRequest(
    val feeRate: BigDecimal? = null,
    val depositDays: Int? = null,
)

data class CardCompanyResponse(
    val id: UUID,
    val name: String,
    val feeRate: BigDecimal,
    val depositDays: Int,
    val isActive: Boolean,
)

data class UserPreferencesResponse(
    val bottomNavItems: List<String>,
)

data class UpdateBottomNavRequest(
    @field:NotNull(message = "항목은 필수입니다")
    @field:Size(min = 4, max = 6, message = "하단바 항목은 4~6개여야 합니다")
    val items: List<String>?,
)

data class PushSubscribeRequest(
    @field:NotBlank(message = "토큰(endpoint)은 필수입니다")
    val endpoint: String?,
    val p256dh: String? = null,
    val auth: String? = null,
    val userAgent: String? = null,
)

data class PushStatusResponse(
    val subscribed: Boolean,
)
