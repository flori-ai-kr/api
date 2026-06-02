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

/** 유저 상세의 인증 이력 1건(요약). */
data class AdminVerificationBrief(
    val status: String,
    val submittedAt: Instant?,
    val reviewedAt: Instant?,
    val rejectReason: String?,
)

/** 운영 콘솔 유저 상세 드릴다운(프로필·구독·인증이력·매출요약). */
data class AdminUserDetail(
    val id: Long,
    val email: String?,
    val nickname: String?,
    val isActive: Boolean,
    val isAdmin: Boolean,
    val createdAt: Instant?,
    val storeName: String?,
    val regionSido: String?,
    val regionSigungu: String?,
    val subscriptionStatus: String?,
    val verifications: List<AdminVerificationBrief>,
    val salesCount: Long,
    val salesTotal: Long,
    val lastSaleDate: java.time.LocalDate?,
)
