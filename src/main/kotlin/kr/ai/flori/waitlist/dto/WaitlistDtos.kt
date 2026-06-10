package kr.ai.flori.waitlist.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** 사전등록 요청. email·shopName 모두 필수. email은 서버에서 trim+소문자 정규화. */
data class WaitlistRegisterRequest(
    @field:NotBlank(message = "이메일을 입력해 주세요")
    @field:Email(message = "이메일 형식이 올바르지 않습니다")
    @field:Size(max = 254, message = "이메일이 너무 깁니다")
    val email: String,
    @field:NotBlank(message = "가게명을 입력해 주세요")
    @field:Size(max = 50, message = "가게명은 50자 이내여야 합니다")
    val shopName: String,
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
