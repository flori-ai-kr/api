package kr.ai.flori.admin.service

import kr.ai.flori.admin.dto.AdminUserDetail
import kr.ai.flori.admin.dto.AdminUserPage
import kr.ai.flori.admin.dto.AdminUserRow
import kr.ai.flori.admin.dto.AdminVerificationBrief
import kr.ai.flori.admin.error.AdminErrorCode
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.user.repository.UserRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 운영 콘솔 유저 조회/운영. cross-tenant — @RequiresAdmin 하위에서만 호출된다.
 * 목록은 JdbcTemplate 조인 projection(users ⨝ user_profiles ⨝ 최신 verification).
 */
@Service
class AdminUserService(
    private val jdbc: JdbcTemplate,
    private val userRepository: UserRepository,
) {
    @Transactional(readOnly = true)
    fun list(
        query: String?,
        page: Int,
        size: Int,
    ): AdminUserPage {
        // raw SQL LIMIT/OFFSET 직빌딩이라 Pageable 기반 Paging 헬퍼 적용 대상이 아님 — 보정만 동일 규칙 유지.
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, MAX_PAGE_SIZE)
        val like = query?.takeIf { it.isNotBlank() }?.let { "%$it%" }
        val total =
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM users u WHERE (?::text IS NULL OR u.email ILIKE ? OR u.nickname ILIKE ?)",
                Long::class.java,
                like,
                like,
                like,
            ) ?: 0
        val rows =
            jdbc.query(
                "$ROW_SELECT WHERE (?::text IS NULL OR u.email ILIKE ? OR u.nickname ILIKE ?) " +
                    "ORDER BY u.id DESC LIMIT ? OFFSET ?",
                ROW_MAPPER,
                like,
                like,
                like,
                safeSize,
                safePage * safeSize,
            )
        return AdminUserPage(rows, safePage, safeSize, total)
    }

    @Transactional
    fun setActive(
        id: Long,
        active: Boolean,
    ): AdminUserRow {
        // 운영자 본인 계정의 비활성화 차단(자기 잠금 방지).
        if (!active && TenantContext.currentUserId() == id) {
            throw AppException(AdminErrorCode.CANNOT_DEACTIVATE_SELF)
        }
        val user = userRepository.findById(id).orElseThrow { AppException(AdminErrorCode.USER_NOT_FOUND) }
        user.isActive = active
        // saveAndFlush: JdbcTemplate(native SQL) 읽기는 Hibernate autoflush를 트리거하지 않으므로
        // rowById의 raw SQL이 변경을 보도록 명시적으로 DB에 flush한다.
        userRepository.saveAndFlush(user)
        return rowById(id)
    }

    private fun rowById(id: Long): AdminUserRow =
        jdbc
            .query("$ROW_SELECT WHERE u.id = ?", ROW_MAPPER, id)
            .firstOrNull() ?: throw AppException(AdminErrorCode.USER_NOT_FOUND)

    /** 드릴다운 상세: 프로필·인증이력·매출요약. cross-tenant. */
    @Transactional(readOnly = true)
    fun detail(id: Long): AdminUserDetail {
        val base = baseDetail(id)
        val sales = salesSummary(id)
        return AdminUserDetail(
            id = id,
            email = base.email,
            nickname = base.nickname,
            isActive = base.isActive,
            isAdmin = base.isAdmin,
            createdAt = base.createdAt,
            storeName = base.storeName,
            regionSido = base.regionSido,
            regionSigungu = base.regionSigungu,
            verifications = verificationBriefs(id),
            salesCount = sales.count,
            salesTotal = sales.total,
            lastSaleDate = sales.lastDate,
        )
    }

    private fun baseDetail(id: Long): BaseDetail =
        jdbc
            .query(
                """
                SELECT u.email, u.nickname, u.is_active, u.is_admin, u.created_at,
                       p.store_name, p.region_sido, p.region_sigungu
                FROM users u
                LEFT JOIN user_profiles p ON p.user_id = u.id
                WHERE u.id = ?
                """.trimIndent(),
                { rs, _ ->
                    BaseDetail(
                        email = rs.getString("email"),
                        nickname = rs.getString("nickname"),
                        isActive = rs.getBoolean("is_active"),
                        isAdmin = rs.getBoolean("is_admin"),
                        createdAt = rs.getTimestamp("created_at")?.toInstant(),
                        storeName = rs.getString("store_name"),
                        regionSido = rs.getString("region_sido"),
                        regionSigungu = rs.getString("region_sigungu"),
                    )
                },
                id,
            ).firstOrNull() ?: throw AppException(AdminErrorCode.USER_NOT_FOUND)

    private fun verificationBriefs(id: Long): List<AdminVerificationBrief> =
        jdbc.query(
            """
            SELECT status, created_at, reviewed_at, reject_reason
            FROM business_verifications WHERE user_id = ? ORDER BY created_at DESC
            """.trimIndent(),
            { rs, _ ->
                AdminVerificationBrief(
                    status = rs.getString("status"),
                    submittedAt = rs.getTimestamp("created_at")?.toInstant(),
                    reviewedAt = rs.getTimestamp("reviewed_at")?.toInstant(),
                    rejectReason = rs.getString("reject_reason"),
                )
            },
            id,
        )

    private fun salesSummary(id: Long): SalesSummary =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) AS cnt, COALESCE(SUM(amount), 0) AS total, MAX(date) AS last_date
            FROM sales WHERE user_id = ? AND payment_method_id IS NOT NULL
            """.trimIndent(),
            { rs, _ -> SalesSummary(rs.getLong("cnt"), rs.getLong("total"), rs.getDate("last_date")?.toLocalDate()) },
            id,
        ) ?: SalesSummary(0, 0, null)

    private data class BaseDetail(
        val email: String?,
        val nickname: String?,
        val isActive: Boolean,
        val isAdmin: Boolean,
        val createdAt: java.time.Instant?,
        val storeName: String?,
        val regionSido: String?,
        val regionSigungu: String?,
    )

    private data class SalesSummary(
        val count: Long,
        val total: Long,
        val lastDate: java.time.LocalDate?,
    )

    private companion object {
        const val MAX_PAGE_SIZE = 200

        val ROW_SELECT =
            """
            SELECT u.id, u.email, u.nickname, u.is_active, u.is_admin, u.created_at,
                   p.store_name,
                   (SELECT bv.status FROM business_verifications bv
                      WHERE bv.user_id = u.id ORDER BY bv.created_at DESC LIMIT 1) AS verification_status
            FROM users u
            LEFT JOIN user_profiles p ON p.user_id = u.id
            """.trimIndent()

        val ROW_MAPPER =
            RowMapper { rs, _ ->
                AdminUserRow(
                    id = rs.getLong("id"),
                    email = rs.getString("email"),
                    nickname = rs.getString("nickname"),
                    storeName = rs.getString("store_name"),
                    isActive = rs.getBoolean("is_active"),
                    isAdmin = rs.getBoolean("is_admin"),
                    verificationStatus = rs.getString("verification_status"),
                    createdAt = rs.getTimestamp("created_at")?.toInstant(),
                )
            }
    }
}
