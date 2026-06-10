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

data class ReservationKpi(
    val total: Long,
    val totalDeltaPct: Int,
    val dailyAvg: Double,
    // 0=일요일..6=토요일, 데이터 없으면 -1
    val busiestDow: Int,
    val busiestDowPct: Int,
    // "15-17" 형식, 데이터 없으면 ""
    val peakHourBucket: String,
    val peakHourPct: Int,
)

data class ReservationTimePoint(
    val date: LocalDate,
    val count: Long,
)

data class HeatCell(
    val dow: Int,
    val hourBucket: String,
    val count: Long,
)

data class DowCount(
    val dow: Int,
    val count: Long,
)

data class HourCount(
    val hourBucket: String,
    val count: Long,
)

data class ReservationStatisticsResponse(
    val kpi: ReservationKpi,
    val timeseries: List<ReservationTimePoint>,
    val heatmap: List<HeatCell>,
    val dowDistribution: List<DowCount>,
    val hourDistribution: List<HourCount>,
)

data class CustomerKpi(
    // 기간 내 구매한 distinct 고객 수
    val total: Long,
    val newCustomers: Long,
    val newDelta: Long,
    val returningCustomers: Long,
    val returningDelta: Long,
    val returningRatePct: Int,
)

data class CustomerNewPoint(
    val date: LocalDate,
    val newCustomers: Long,
)

data class GradeCount(
    val grade: String,
    val count: Long,
)

data class GenderCount(
    val gender: String?,
    val count: Long,
)

data class TopCustomer(
    val customerId: Long?,
    val name: String,
    val grade: String,
    val purchaseCount: Long,
    val totalAmount: Long,
)

data class CustomerStatisticsResponse(
    val kpi: CustomerKpi,
    val timeseries: List<CustomerNewPoint>,
    val gradeDistribution: List<GradeCount>,
    val genderDistribution: List<GenderCount>,
    val topCustomers: List<TopCustomer>,
)
