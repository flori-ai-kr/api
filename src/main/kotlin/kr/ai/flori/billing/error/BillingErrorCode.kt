package kr.ai.flori.billing.error

import kr.ai.flori.common.error.ErrorCode
import org.springframework.http.HttpStatus

/** 빌링 도메인 에러. 코드 체계 E-BILLING-NNN. */
enum class BillingErrorCode(
    override val code: String,
    override val status: HttpStatus,
    override val defaultMessage: String,
) : ErrorCode {
    BILLING_KEY_ISSUE_FAILED("E-BILLING-001", HttpStatus.BAD_GATEWAY, "카드 등록에 실패했습니다. 잠시 후 다시 시도해 주세요"),
    PAYMENT_REJECTED("E-BILLING-002", HttpStatus.PAYMENT_REQUIRED, "결제가 거절되었습니다. 카드를 확인해 주세요"),
    SUBSCRIPTION_STATE("E-BILLING-003", HttpStatus.CONFLICT, "현재 구독 상태에서 처리할 수 없습니다"),
    COUPON_NOT_FOUND("E-BILLING-101", HttpStatus.NOT_FOUND, "유효하지 않은 쿠폰입니다"),
    COUPON_NOT_IN_PERIOD("E-BILLING-102", HttpStatus.BAD_REQUEST, "사용 기간이 아닌 쿠폰입니다"),
    COUPON_EXHAUSTED("E-BILLING-103", HttpStatus.CONFLICT, "이미 사용했거나 소진된 쿠폰입니다"),
    COUPON_DISABLED("E-BILLING-104", HttpStatus.CONFLICT, "사용할 수 없는 쿠폰입니다"),
    WEBHOOK_SIGNATURE("E-BILLING-201", HttpStatus.UNAUTHORIZED, "웹훅 서명 검증에 실패했습니다"),
}
