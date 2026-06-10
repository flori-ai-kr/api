package kr.ai.flori.interview.error

import kr.ai.flori.common.error.ErrorCode
import org.springframework.http.HttpStatus

/** 유저 인터뷰 모집 도메인 에러 코드. */
enum class InterviewErrorCode(
    override val code: String,
    override val status: HttpStatus,
    override val defaultMessage: String,
) : ErrorCode {
    ALREADY_APPLIED("E-IV-001", HttpStatus.CONFLICT, "이미 인터뷰 신청을 받았어요. 곧 연락드릴게요!"),
}
