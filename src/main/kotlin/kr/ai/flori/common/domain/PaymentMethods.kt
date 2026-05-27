package kr.ai.flori.common.domain

/**
 * 결제방식 도메인 값(SSOT). 매출은 미수(unpaid)를 포함, 지출은 제외.
 * 설정/매출/지출 검증에서 공용으로 사용한다.
 */
object PaymentMethods {
    const val UNPAID = "unpaid"

    /** 매출 허용 결제방식(미수 포함). */
    val SALE = setOf("cash", "card", "transfer", "naverpay", "kakaopay", UNPAID)

    /** 지출 허용 결제방식(미수 제외). */
    val EXPENSE = setOf("cash", "card", "transfer", "naverpay", "kakaopay")
}
