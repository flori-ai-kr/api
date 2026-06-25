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
    /** 갤러리 저장 용량 한도 초과 — 업로드 차단(증설 요청 유도). */
    QUOTA_EXCEEDED("E-STG-001", HttpStatus.CONFLICT, "저장 용량이 부족합니다. 관리자에게 증설을 요청해 주세요"),
}
