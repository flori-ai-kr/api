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

data class SubscriptionCounts(
    val active: Long,
    val inGrace: Long,
    val expired: Long,
    val none: Long,
)

data class VerificationCounts(
    val pending: Long,
    val approved: Long,
    val rejected: Long,
)

/** 운영 콘솔 개요. cross-tenant 단일 집계. */
data class AdminOverviewResponse(
    val users: UserCounts,
    val sales: SalesCounts,
    val subscriptions: SubscriptionCounts,
    val verifications: VerificationCounts,
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
