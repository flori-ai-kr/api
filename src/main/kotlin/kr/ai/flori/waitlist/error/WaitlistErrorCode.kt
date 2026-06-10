package kr.ai.flori.waitlist.error

import kr.ai.flori.common.error.ErrorCode
import org.springframework.http.HttpStatus

/** 사전등록 도메인 에러 코드. */
enum class WaitlistErrorCode(
    override val code: String,
    override val status: HttpStatus,
    override val defaultMessage: String,
) : ErrorCode {
    ALREADY_REGISTERED("E-WL-001", HttpStatus.CONFLICT, "이미 사전등록된 이메일이에요"),
    CLOSED("E-WL-002", HttpStatus.CONFLICT, "사전등록이 마감되었어요 (선착순 100명)"),
}
