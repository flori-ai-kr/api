package kr.ai.flori.deposits.dto

import jakarta.validation.constraints.NotEmpty
import java.util.UUID

data class ConfirmDepositsRequest(
    @field:NotEmpty(message = "대상 매출 ID가 필요합니다")
    val ids: List<UUID>?,
)

data class DepositSummaryResponse(
    val pendingCount: Int,
    val pendingAmount: Long,
    val completedCount: Int,
    val completedAmount: Long,
)
