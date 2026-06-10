package kr.ai.flori.verification.domain

/**
 * 사업자 인증 상태. DB(business_verifications.status)에는 PENDING/APPROVED/REJECTED만 저장된다.
 * NONE은 응답 전용(인증 이력 없음)으로 DTO에서만 사용하며 여기 포함하지 않는다.
 */
enum class BusinessVerificationStatuses {
    PENDING,
    APPROVED,
    REJECTED,
}
