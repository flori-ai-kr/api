package kr.ai.flori.auth.entity

/**
 * refresh 토큰 수명 상태. DB CHECK 제약 값과 일치하며, 종료 사유별 세션 통계의 차원이 된다.
 * 도메인 상수 패턴은 [kr.ai.flori.common.domain.ReservationStatuses]와 동일.
 */
object RefreshTokenStatuses {
    const val ACTIVE = "ACTIVE" // 유효한 현재 토큰
    const val ROTATED = "ROTATED" // refresh 회전으로 교체됨
    const val LOGGED_OUT = "LOGGED_OUT" // 로그아웃으로 무효화
    const val EXPIRED = "EXPIRED" // 만료 처리됨

    val ALL = setOf(ACTIVE, ROTATED, LOGGED_OUT, EXPIRED)
}
