package kr.ai.flori.storage.error

import kr.ai.flori.common.error.ErrorCode
import org.springframework.http.HttpStatus

/**
 * 스토리지(이미지 용량) 도메인 에러 코드. 웹/앱은 이 `code`로 분기한다.
 */
enum class StorageErrorCode(
    override val code: String,
    override val status: HttpStatus,
    override val defaultMessage: String,
) : ErrorCode {
    QUOTA_EXCEEDED("E-STG-001", HttpStatus.CONFLICT, "저장 용량이 부족합니다. 관리자에게 증설을 요청해 주세요"),
    DUPLICATE_PENDING("E-STG-002", HttpStatus.CONFLICT, "이미 처리 중인 증설 요청이 있습니다"),
    REQUEST_NOT_FOUND("E-STG-003", HttpStatus.NOT_FOUND, "증설 요청을 찾을 수 없습니다"),
}
