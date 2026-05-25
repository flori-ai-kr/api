package kr.ai.flori.subscriptions.service

/**
 * RevenueCat 웹훅 이벤트 → 내부 구독 상태 매핑(순수 함수, DB·컨텍스트 무관 → 단위테스트 용이).
 *
 * 상태 전이 규칙(SPEC-SERVER-014):
 * - INITIAL_PURCHASE / RENEWAL / PRODUCT_CHANGE / UNCANCELLATION / NON_RENEWING_PURCHASE → active
 * - CANCELLATION → active 유지 (자동갱신만 해제, 기간말까지 접근 유지 → EXPIRATION에서 만료)
 * - BILLING_ISSUE → in_grace (결제 유예)
 * - EXPIRATION → expired
 * - REFUND → none (환불 — 즉시 권한 회수)
 * - 그 외(TRANSFER 등) → null (상태 변경 없음)
 */
object SubscriptionStatusMapper {
    const val ACTIVE = "active"
    const val IN_GRACE = "in_grace"
    const val EXPIRED = "expired"
    const val NONE = "none"

    private val TO_ACTIVE =
        setOf("INITIAL_PURCHASE", "RENEWAL", "PRODUCT_CHANGE", "UNCANCELLATION", "NON_RENEWING_PURCHASE", "CANCELLATION")

    /** 이벤트 타입에 대응하는 상태. 변경 불필요한 이벤트는 null. */
    fun mapStatus(eventType: String?): String? =
        when (eventType?.uppercase()) {
            in TO_ACTIVE -> ACTIVE
            "BILLING_ISSUE" -> IN_GRACE
            "EXPIRATION" -> EXPIRED
            "REFUND" -> NONE
            else -> null
        }

    /** RevenueCat store 코드 → apple|google. IAP 범위상 Apple/Google만 취급(CHECK 제약 충족). */
    fun mapStore(rawStore: String?): String = if (rawStore?.uppercase()?.contains("PLAY") == true) "google" else "apple"
}
