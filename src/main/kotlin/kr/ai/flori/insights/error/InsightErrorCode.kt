package kr.ai.flori.insights.error

import kr.ai.flori.common.error.ErrorCode
import org.springframework.http.HttpStatus

/**
 * 인사이트(정보 피드) 도메인 전용 에러 코드. 웹/앱은 이 `code`로 분기한다.
 *
 * 트렌드/지원사업/경매 시세는 공유 읽기, 스크랩은 개인(user_id) 데이터다.
 */
enum class InsightErrorCode(
    override val code: String,
    override val status: HttpStatus,
    override val defaultMessage: String,
) : ErrorCode {
    INVALID_TARGET_TYPE("E-INS-001", HttpStatus.BAD_REQUEST, "올바르지 않은 스크랩 대상 유형입니다"),
    SCRAP_TARGET_NOT_FOUND("E-INS-002", HttpStatus.NOT_FOUND, "스크랩 대상을 찾을 수 없습니다"),
    NO_AUCTION_DATA("E-INS-003", HttpStatus.NOT_FOUND, "경매 시세 데이터가 없습니다"),
}
