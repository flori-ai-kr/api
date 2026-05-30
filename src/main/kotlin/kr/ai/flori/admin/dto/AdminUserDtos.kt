package kr.ai.flori.admin.dto

import jakarta.validation.constraints.NotNull
import java.time.Instant

/** 운영 콘솔 유저 1행(구독·검증 상태 조인). */
data class AdminUserRow(
    val id: Long,
    val email: String?,
    val nickname: String?,
    val storeName: String?,
    val isActive: Boolean,
    val isAdmin: Boolean,
    val subscriptionStatus: String?,
    val verificationStatus: String?,
    val createdAt: Instant?,
)

data class AdminUserPage(
    val rows: List<AdminUserRow>,
    val page: Int,
    val size: Int,
    val total: Long,
)

data class SetActiveRequest(
    @field:NotNull(message = "active는 필수입니다")
    val active: Boolean?,
)
