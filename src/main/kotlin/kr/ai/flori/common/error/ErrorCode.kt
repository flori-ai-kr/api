package kr.ai.flori.common.error

import org.springframework.http.HttpStatus

/**
 * 안정적인 에러 코드 계약. 모든 도메인 에러 코드 enum이 구현한다.
 *
 * 코드 체계: `E-{DOMAIN}-{NNN}` (예: `E-CMN-001`, `E-AUTH-002`).
 * - 횡단(공통) 코드는 [CommonErrorCode] (common/error)에 둔다.
 * - 도메인 전용 코드는 각 도메인 패키지의 `<domain>/error`에 둔다(예: auth/error/AuthErrorCode).
 *
 * `code`는 웹/앱 클라이언트가 분기에 쓰는 안정적 식별자이므로 한 번 공개되면 바꾸지 않는다.
 */
interface ErrorCode {
    /** 클라이언트 분기용 안정적 코드. 예: "E-CMN-001". */
    val code: String

    /** 매핑될 HTTP 상태. */
    val status: HttpStatus

    /** 기본(폴백) 사용자 메시지. 호출부에서 더 구체적 메시지를 줄 수 있다. */
    val defaultMessage: String
}
