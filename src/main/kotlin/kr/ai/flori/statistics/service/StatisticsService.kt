package kr.ai.flori.statistics.service

import kr.ai.flori.statistics.dto.CustomerStatisticsResponse
import kr.ai.flori.statistics.dto.ExpensesStatisticsResponse
import kr.ai.flori.statistics.dto.ReservationStatisticsResponse
import kr.ai.flori.statistics.dto.SalesStatisticsResponse
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * 통계 파사드. 도메인별 통계 서비스(매출·지출·예약·고객)로 위임만 한다.
 * 실제 집계·SQL은 각 도메인 서비스가 소유하며, 교차 도메인 계산은 [StatisticsSupport]가 담당한다.
 */
@Service
class StatisticsService(
    private val sales: SalesStatisticsService,
    private val expenses: ExpensesStatisticsService,
    private val reservations: ReservationStatisticsService,
    private val customers: CustomerStatisticsService,
) {
    fun salesStatistics(
        from: LocalDate,
        to: LocalDate,
    ): SalesStatisticsResponse = sales.salesStatistics(from, to)

    fun expensesStatistics(
        from: LocalDate,
        to: LocalDate,
    ): ExpensesStatisticsResponse = expenses.expensesStatistics(from, to)

    fun reservationStatistics(
        from: LocalDate,
        to: LocalDate,
    ): ReservationStatisticsResponse = reservations.reservationStatistics(from, to)

    fun customerStatistics(
        from: LocalDate,
        to: LocalDate,
    ): CustomerStatisticsResponse = customers.customerStatistics(from, to)
}
