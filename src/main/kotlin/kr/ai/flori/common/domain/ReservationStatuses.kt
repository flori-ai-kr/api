package kr.ai.flori.common.domain

/**
 * 예약 상태 값. DB 컬럼/앱 계약과 일치한다.
 * 도메인 간 재사용을 위해 공통 상수로 둔다([DepositStatuses]/[PaymentMethods]와 동일 패턴).
 */
object ReservationStatuses {
    const val PENDING = "pending"
    const val CONFIRMED = "confirmed"
    const val COMPLETED = "completed"
    const val CANCELLED = "cancelled"

    val ALL = setOf(PENDING, CONFIRMED, COMPLETED, CANCELLED)
}
