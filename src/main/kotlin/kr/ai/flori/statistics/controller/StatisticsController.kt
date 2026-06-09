package kr.ai.flori.statistics.controller

import kr.ai.flori.statistics.dto.ExpensesStatisticsResponse
import kr.ai.flori.statistics.dto.SalesStatisticsResponse
import kr.ai.flori.statistics.service.StatisticsService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/statistics")
class StatisticsController(
    private val service: StatisticsService,
) {
    @GetMapping("/sales")
    fun sales(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): SalesStatisticsResponse = service.salesStatistics(from, to)

    @GetMapping("/expenses")
    fun expenses(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): ExpensesStatisticsResponse = service.expensesStatistics(from, to)
}
