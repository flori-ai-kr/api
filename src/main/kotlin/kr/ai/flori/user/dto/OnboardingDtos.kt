package kr.ai.flori.user.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import kr.ai.flori.common.validation.FieldLimits

/**
 * 온보딩 제출 요청. 옵션 값(시도/나이대/관심사/주력)의 enum 검증은 웹이 소유하고,
 * 서버는 자유 문자열로 저장한다. 필수 필드(name, regionSido)만 NotBlank 검증한다.
 * "건너뛰기" 경로는 name + regionSido만 전송하므로 선택 필드는 부재를 허용해야 한다.
 */
@Schema(description = "온보딩 제출 요청. 가게 프로필을 저장한다.")
data class OnboardingRequest(
    @field:NotBlank(message = "가게명은 필수입니다")
    @field:Size(max = FieldLimits.STORE_NAME, message = "가게명이 너무 깁니다")
    @field:Schema(description = "가게명", example = "헤이즐 플라워")
    val name: String,
    @field:NotBlank(message = "시/도는 필수입니다")
    @field:Size(max = FieldLimits.REGION, message = "시/도가 너무 깁니다")
    @field:Schema(description = "시/도(웹 enum 값 문자열)", example = "서울특별시")
    val regionSido: String,
    @field:Size(max = FieldLimits.EMAIL, message = "이메일이 너무 깁니다")
    @field:Schema(description = "이메일. 보내면 변경, 생략하면 기존 이메일 유지.", example = "admin@example.com")
    val email: String? = null,
    @field:Size(max = FieldLimits.NAME, message = "닉네임이 너무 깁니다")
    @field:Schema(
        description = "계정 표시명(닉네임). 보내면 변경, 생략하면 기존 닉네임 유지. 전역 유일.",
        example = "헤이즐",
    )
    val nickname: String? = null,
    @field:Size(max = FieldLimits.REGION, message = "시군구가 너무 깁니다")
    @field:Schema(description = "시군구(선택)", example = "강남구")
    val regionSigungu: String? = null,
    @field:Size(max = FieldLimits.AGE_RANGE, message = "나이대 값이 너무 깁니다")
    @field:Schema(description = "사장님 나이대(선택, 단일)", example = "30대")
    val ownerAgeRange: String? = null,
    @field:Size(max = FieldLimits.PROFILE_LIST, message = "관심사가 너무 많습니다")
    @field:Schema(description = "관심사(선택, 복수)", example = "[\"웨딩\",\"개업화환\"]")
    val interests: List<
        @Size(max = FieldLimits.LABEL, message = "관심사 값이 너무 깁니다")
        String,
    >? = null,
    @field:Size(max = FieldLimits.PROFILE_LIST, message = "주력이 너무 많습니다")
    @field:Schema(description = "가게 주력(선택, 복수)", example = "[\"꽃다발\",\"화분\"]")
    val specialties: List<
        @Size(max = FieldLimits.LABEL, message = "주력 값이 너무 깁니다")
        String,
    >? = null,
    @field:Size(max = FieldLimits.IMAGE_URL, message = "이미지 URL이 너무 깁니다")
    @field:Schema(description = "프로필 이미지 URL(선택)")
    val profileImageUrl: String? = null,
)

/** 가게 프로필 응답. /me 응답에 포함되어 프로필 편집 화면에서 재사용한다. */
@Schema(description = "가게 프로필")
data class ProfileResponse(
    @field:Schema(description = "가게명")
    val storeName: String,
    @field:Schema(description = "시/도")
    val regionSido: String,
    @field:Schema(description = "시군구(미설정 시 null)")
    val regionSigungu: String?,
    @field:Schema(description = "사장님 나이대(미설정 시 null)")
    val ownerAgeRange: String?,
    @field:Schema(description = "관심사 목록")
    val interests: List<String>,
    @field:Schema(description = "가게 주력 목록")
    val specialties: List<String>,
    @field:Schema(description = "프로필 이미지 URL(미설정 시 null)")
    val profileImageUrl: String? = null,
    @field:Schema(description = "인앱 투어 완료 여부")
    val tourCompleted: Boolean,
)

/** GET /me/profile 전용 응답. 프로필 편집 화면에서 사용한다. */
@Schema(description = "전체 프로필 정보")
data class FullProfileResponse(
    val id: Long,
    val name: String,
    val nickname: String,
    val email: String,
    val profileImageUrl: String?,
    val regionSido: String,
    val regionSigungu: String?,
    val ownerAgeRange: String?,
    val interests: List<String>,
    val specialties: List<String>,
)

/** POST /me/profile/upload-target 요청. */
@Schema(description = "프로필 이미지 업로드 대상 요청")
data class ProfileUploadTargetRequest(
    @field:NotBlank(message = "contentType은 필수입니다")
    @field:Schema(description = "이미지 MIME 타입", example = "image/jpeg")
    val contentType: String,
)

/** POST /me/profile/upload-target 응답. */
@Schema(description = "프로필 이미지 업로드 URL")
data class ProfileUploadTargetResponse(
    val uploadUrl: String,
    val publicUrl: String,
)

/** DELETE /me 요청 (탈퇴 사유). */
@Schema(description = "계정 탈퇴 요청")
data class DeleteAccountRequest(
    @field:Size(max = FieldLimits.MEMO, message = "사유가 너무 깁니다")
    @field:Schema(description = "탈퇴 사유(선택)")
    val reason: String? = null,
    @field:Size(max = FieldLimits.MEMO, message = "상세 사유가 너무 깁니다")
    @field:Schema(description = "기타 상세 사유(선택)")
    val detail: String? = null,
)
