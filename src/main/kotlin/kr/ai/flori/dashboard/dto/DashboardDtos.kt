package kr.ai.flori.dashboard.dto

import kr.ai.flori.reservations.dto.ReservationResponse
import kr.ai.flori.sales.dto.SaleResponse

/** 매출 요약(미수 제외). 금액은 Long. */
data class DashboardSummary(
    val totalAmount: Long,
    val cardAmount: Long,
    val cashAmount: Long,
    val transferAmount: Long,
    val naverpayAmount: Long,
    val kakaopayAmount: Long,
)

data class CategoryStat(
    val categoryId: Long?,
    val label: String,
    val count: Long,
    val amount: Long,
    val percentage: Int,
)

data class PaymentMethodStat(
    val paymentMethodId: Long?,
    val label: String,
    val count: Long,
    val amount: Long,
    val percentage: Int,
)

data class ChannelStat(
    val channelId: Long?,
    val label: String,
    val count: Long,
    val amount: Long,
    val percentage: Int,
)

data class ExpenseCategoryStat(
    val categoryId: Long?,
    val label: String,
    val amount: Long,
    val percentage: Int,
)

data class CustomerStat(
    val totalCustomers: Long,
    val returningCustomers: Long,
    val newCustomers: Long,
)

data class CategoryOption(
    val value: String,
    val label: String,
)

data class TodayDashboardResponse(
    val summary: DashboardSummary,
    val upcomingReservations: List<ReservationResponse>,
    val triggeredReminders: List<ReservationResponse>,
    val recentSales: List<SaleResponse>,
    val saleCategories: List<CategoryOption>,
)

data class MonthDashboardResponse(
    val summary: DashboardSummary,
    val expenseTotal: Long,
    val categoryStats: List<CategoryStat>,
    val paymentStats: List<PaymentMethodStat>,
    val channelStats: List<ChannelStat>,
    val customerStats: CustomerStat,
    val expenseStats: List<ExpenseCategoryStat>,
)
