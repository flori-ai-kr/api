package kr.ai.flori.interview.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * 유저 인터뷰 참여 신청 요청. name·phone 모두 필수.
 * phone은 서버에서 숫자만 남겨 정규화 후 UNIQUE 검사.
 */
data class InterviewApplyRequest(
    @field:NotBlank(message = "이름을 입력해 주세요")
    @field:Size(max = 50, message = "이름은 50자 이내여야 합니다")
    val name: String,
    @field:NotBlank(message = "전화번호를 입력해 주세요")
    @field:Pattern(
        regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$",
        message = "전화번호 형식이 올바르지 않습니다",
    )
    val phone: String,
)

/** 인터뷰 신청 응답. */
data class InterviewApplyResponse(
    val applied: Boolean,
)
