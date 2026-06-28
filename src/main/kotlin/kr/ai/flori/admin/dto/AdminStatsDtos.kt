package kr.ai.flori.admin.dto

data class UserCounts(
    val total: Long,
    val active: Long,
    val onboarded: Long,
)

data class SalesCounts(
    val entryCount: Long,
    val totalAmount: Long,
    val last30dCount: Long,
)

data class VerificationCounts(
    val pending: Long,
    val approved: Long,
    val rejected: Long,
)

data class SubscriptionCounts(
    val active: Long,
    val trialing: Long,
    val inGrace: Long,
    val expired: Long,
)

/** 운영 콘솔 개요. cross-tenant 단일 집계. */
data class AdminOverviewResponse(
    val users: UserCounts,
    val sales: SalesCounts,
    val verifications: VerificationCounts,
    val subscriptions: SubscriptionCounts,
    val comparison: OverviewComparison?,
)

/** 선택 기간 대비 직전 동기간 증감(%). 직전 기간 0이거나 range=all이면 null. */
data class OverviewComparison(
    val usersChangePct: Double?,
    val salesCountChangePct: Double?,
)

/** 일별 시계열 1점. */
data class TimeseriesPoint(
    val date: java.time.LocalDate,
    val count: Long,
)

/** 활성화 퍼널 1단계. */
data class FunnelStage(
    val key: String,
    val label: String,
    val count: Long,
)

/** 탈퇴 사유 집계 1건(최근 N일). */
data class ChurnReasonSlice(
    val reason: String,
    val count: Long,
)

/** 주간 리텐션 코호트 1행. cohortWeek 가입 주, retention[i] = W{i} 잔존율(0~1). */
data class RetentionCohortRow(
    val cohortWeek: java.time.LocalDate,
    val cohortSize: Long,
    val retention: List<Double?>,
)
