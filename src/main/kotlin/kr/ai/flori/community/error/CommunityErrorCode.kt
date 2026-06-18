package kr.ai.flori.community.error

import kr.ai.flori.common.error.ErrorCode
import org.springframework.http.HttpStatus

/**
 * 커뮤니티 도메인 전용 에러 코드. 웹/앱은 이 `code`로 분기한다.
 */
enum class CommunityErrorCode(
    override val code: String,
    override val status: HttpStatus,
    override val defaultMessage: String,
) : ErrorCode {
    POST_NOT_FOUND("E-CMNT-001", HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다"),
    COMMENT_NOT_FOUND("E-CMNT-002", HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다"),
    INVALID_CATEGORY("E-CMNT-003", HttpStatus.BAD_REQUEST, "올바르지 않은 카테고리입니다"),
    NOTICE_ADMIN_ONLY("E-CMNT-004", HttpStatus.FORBIDDEN, "공지는 관리자만 작성할 수 있습니다"),
    FORBIDDEN("E-CMNT-005", HttpStatus.FORBIDDEN, "권한이 없습니다"),
    INVALID_PARENT("E-CMNT-006", HttpStatus.BAD_REQUEST, "올바르지 않은 부모 댓글입니다"),
    PIN_ADMIN_ONLY("E-CMNT-007", HttpStatus.FORBIDDEN, "게시글 고정은 관리자만 할 수 있습니다"),
}
