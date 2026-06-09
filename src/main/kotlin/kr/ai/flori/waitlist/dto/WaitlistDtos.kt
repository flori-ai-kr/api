package kr.ai.flori.waitlist.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/** 사전등록 요청. phone은 하이픈 허용(서버에서 정규화). */
data class WaitlistRegisterRequest(
    @field:NotBlank(message = "가게명을 입력해 주세요")
    @field:Size(max = 50, message = "가게명은 50자 이내여야 합니다")
    val shopName: String,
    @field:Pattern(regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$", message = "전화번호 형식이 올바르지 않습니다")
    val phone: String,
)

/** 사전등록 응답(등록 직후). */
data class WaitlistRegisterResponse(
    val count: Long,
    val capacity: Int,
    val closed: Boolean,
)

/** 카운트 조회 응답. */
data class WaitlistCountResponse(
    val count: Long,
    val capacity: Int,
    val closed: Boolean,
)
