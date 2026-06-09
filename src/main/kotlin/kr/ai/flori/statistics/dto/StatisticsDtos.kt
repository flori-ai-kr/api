package kr.ai.flori.statistics.dto

import java.time.LocalDate

data class DistributionItem(
    val id: Long?,
    val label: String,
    val amount: Long,
    val count: Long,
    val percentage: Int,
)

data class SalesKpi(
    val totalAmount: Long,
    val totalAmountDeltaPct: Int,
    val count: Long,
    val countDelta: Long,
    val avgOrderValue: Long,
    val avgOrderValueDeltaPct: Int,
    val unpaidBalance: Long,
    val unpaidCount: Long,
)

data class SalesTimePoint(
    val date: LocalDate,
    val amount: Long,
    val count: Long,
)

data class SalesStatisticsResponse(
    val kpi: SalesKpi,
    val timeseries: List<SalesTimePoint>,
    val categoryDistribution: List<DistributionItem>,
    val paymentDistribution: List<DistributionItem>,
    val channelDistribution: List<DistributionItem>,
)

data class ExpensesKpi(
    val totalAmount: Long,
    val totalAmountDeltaPct: Int,
    val count: Long,
    val countDelta: Long,
    // 총지출 / 총매출(미수 제외), round, 매출 0이면 0
    val expenseRatioPct: Int,
    // 매출(미수 제외) - 지출
    val netProfit: Long,
    val netProfitDeltaPct: Int,
)

data class ExpensesTimePoint(
    val date: LocalDate,
    val expense: Long,
    val netProfit: Long,
)

data class ExpensesStatisticsResponse(
    val kpi: ExpensesKpi,
    val timeseries: List<ExpensesTimePoint>,
    val categoryDistribution: List<DistributionItem>,
)
