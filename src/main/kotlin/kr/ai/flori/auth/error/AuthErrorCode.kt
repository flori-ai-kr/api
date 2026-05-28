package kr.ai.flori.auth.error

import kr.ai.flori.common.error.ErrorCode
import org.springframework.http.HttpStatus

/**
 * 인증/가입 도메인 전용 에러 코드. 자동 병합 금지 정책상 중복을 신원/이메일/닉네임으로 구분한다.
 *
 * 웹/앱 클라이언트는 이 `code` 값으로 중복 유형을 분기한다(메시지 텍스트가 아니라 코드로 구분).
 */
enum class AuthErrorCode(
    override val code: String,
    override val status: HttpStatus,
    override val defaultMessage: String,
) : ErrorCode {
    /** 같은 (provider, providerId)가 이미 가입됨 → registerToken 재사용 차단. */
    ALREADY_REGISTERED("E-AUTH-001", HttpStatus.CONFLICT, "이미 가입된 계정입니다"),

    /** 이메일이 타 계정에서 사용 중. */
    DUPLICATE_EMAIL("E-AUTH-002", HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다"),

    /** 닉네임(users.nickname 전역 유일)이 타 계정에서 사용 중. */
    DUPLICATE_NICKNAME("E-AUTH-003", HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다"),
}
