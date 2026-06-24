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

    // 커뮤니티 모더레이션
    REPORT_NOT_FOUND("E-ADM-006", HttpStatus.NOT_FOUND, "신고를 찾을 수 없습니다"),
    POST_NOT_FOUND("E-ADM-007", HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다"),
    COMMENT_NOT_FOUND("E-ADM-008", HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다"),
    ALREADY_BANNED("E-ADM-009", HttpStatus.CONFLICT, "이미 차단된 사용자입니다"),
    BAN_NOT_FOUND("E-ADM-010", HttpStatus.NOT_FOUND, "차단 내역을 찾을 수 없습니다"),

    // 브로드캐스트
    BROADCAST_NOT_FOUND("E-ADM-011", HttpStatus.NOT_FOUND, "브로드캐스트를 찾을 수 없습니다"),
    INVALID_BROADCAST_STATE("E-ADM-012", HttpStatus.CONFLICT, "이미 발송되었거나 발송할 수 없는 상태입니다"),

    // 공지 배너
    ANNOUNCEMENT_NOT_FOUND("E-ADM-013", HttpStatus.NOT_FOUND, "공지를 찾을 수 없습니다"),

    // 문의 인박스
    INQUIRY_NOT_FOUND("E-ADM-014", HttpStatus.NOT_FOUND, "문의를 찾을 수 없습니다"),

    // AI 프롬프트 레지스트리(SPEC-AI-008)
    PROMPT_NOT_FOUND("E-ADM-015", HttpStatus.NOT_FOUND, "프롬프트를 찾을 수 없습니다"),
    CANNOT_DELETE_ACTIVE_PROMPT(
        "E-ADM-016",
        HttpStatus.UNPROCESSABLE_ENTITY,
        "활성 프롬프트는 삭제할 수 없습니다. 다른 버전을 먼저 활성화하세요",
    ),
    INVALID_PROMPT_MODEL("E-ADM-017", HttpStatus.UNPROCESSABLE_ENTITY, "허용되지 않은 모델입니다"),
    DUPLICATE_PROMPT_VERSION("E-ADM-018", HttpStatus.CONFLICT, "같은 채널에 이미 존재하는 버전입니다"),
    INVALID_PROMPT_CHANNEL("E-ADM-019", HttpStatus.UNPROCESSABLE_ENTITY, "지원하지 않는 채널입니다"),
}
