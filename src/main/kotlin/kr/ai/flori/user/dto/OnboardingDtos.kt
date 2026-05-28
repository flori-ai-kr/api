package kr.ai.flori.user.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

/**
 * 온보딩 제출 요청. 옵션 값(시도/나이대/관심사/주력)의 enum 검증은 웹이 소유하고,
 * 서버는 자유 문자열로 저장한다. 필수 필드(name, regionSido)만 NotBlank 검증한다.
 * "건너뛰기" 경로는 name + regionSido만 전송하므로 선택 필드는 부재를 허용해야 한다.
 */
@Schema(description = "온보딩 제출 요청. 가게 프로필을 저장한다.")
data class OnboardingRequest(
    @field:NotBlank(message = "가게명은 필수입니다")
    @field:Schema(description = "가게명", example = "헤이즐 플라워")
    val name: String,
    @field:NotBlank(message = "시/도는 필수입니다")
    @field:Schema(description = "시/도(웹 enum 값 문자열)", example = "서울특별시")
    val regionSido: String,
    @field:Schema(
        description = "계정 표시명(닉네임). 보내면 변경, 생략하면 기존 닉네임 유지. 전역 유일.",
        example = "헤이즐",
    )
    val nickname: String? = null,
    @field:Schema(description = "시군구(선택)", example = "강남구")
    val regionSigungu: String? = null,
    @field:Schema(description = "사장님 나이대(선택, 단일)", example = "30대")
    val ownerAgeRange: String? = null,
    @field:Schema(description = "관심사(선택, 복수)", example = "[\"웨딩\",\"개업화환\"]")
    val interests: List<String>? = null,
    @field:Schema(description = "가게 주력(선택, 복수)", example = "[\"꽃다발\",\"화분\"]")
    val specialties: List<String>? = null,
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
)
