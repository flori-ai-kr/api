package kr.ai.flori.verification.error

import kr.ai.flori.common.error.ErrorCode
import org.springframework.http.HttpStatus

/**
 * 사업자 인증 도메인 에러 코드. 웹/앱은 이 `code`로 분기한다.
 */
enum class VerificationErrorCode(
    override val code: String,
    override val status: HttpStatus,
    override val defaultMessage: String,
) : ErrorCode {
    NOT_VERIFIED("E-VRF-001", HttpStatus.FORBIDDEN, "사업자 인증이 필요한 기능입니다"),
    ALREADY_REQUESTED("E-VRF-002", HttpStatus.CONFLICT, "이미 진행 중이거나 완료된 사업자 인증이 있습니다"),
    LICENSE_NOT_OWNED("E-VRF-003", HttpStatus.FORBIDDEN, "본인이 업로드한 등록증만 제출할 수 있습니다"),
    INVALID_LICENSE_TYPE("E-VRF-004", HttpStatus.BAD_REQUEST, "허용되지 않는 파일 형식입니다"),
}
