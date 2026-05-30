package kr.ai.flori.admin.error

import kr.ai.flori.common.error.ErrorCode
import org.springframework.http.HttpStatus

/**
 * 운영 콘솔(admin) 전용 에러 코드. 웹은 이 `code`로 분기한다.
 */
enum class AdminErrorCode(
    override val code: String,
    override val status: HttpStatus,
    override val defaultMessage: String,
) : ErrorCode {
    FORBIDDEN_NOT_ADMIN("E-ADM-001", HttpStatus.FORBIDDEN, "운영자 권한이 필요합니다"),
    VERIFICATION_NOT_FOUND("E-ADM-002", HttpStatus.NOT_FOUND, "사업자 인증 신청을 찾을 수 없습니다"),
    INVALID_VERIFICATION_STATE("E-ADM-003", HttpStatus.CONFLICT, "이미 처리된 신청입니다"),
    USER_NOT_FOUND("E-ADM-004", HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"),
    CANNOT_DEACTIVATE_SELF("E-ADM-005", HttpStatus.UNPROCESSABLE_ENTITY, "자신의 계정은 비활성화할 수 없습니다"),
}
