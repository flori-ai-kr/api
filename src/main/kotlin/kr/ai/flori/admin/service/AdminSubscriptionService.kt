package kr.ai.flori.admin.service

import kr.ai.flori.admin.dto.AdminSubscriptionRow
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 운영 콘솔 구독 현황 목록. cross-tenant — @RequiresAdmin 하위에서만 호출된다. */
@Service
class AdminSubscriptionService(
    private val jdbc: JdbcTemplate,
) {
    @Transactional(readOnly = true)
    fun list(
        status: String?,
        page: Int,
        size: Int,
    ): List<AdminSubscriptionRow> {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, MAX_PAGE_SIZE)
        return jdbc.query(
            """
            SELECT user_id, status, store, product_id, entitlement, current_period_end
            FROM subscriptions
            WHERE (?::text IS NULL OR status = ?)
            ORDER BY current_period_end DESC NULLS LAST
            LIMIT ? OFFSET ?
            """.trimIndent(),
            { rs, _ ->
                AdminSubscriptionRow(
                    userId = rs.getLong("user_id"),
                    status = rs.getString("status"),
                    store = rs.getString("store"),
                    productId = rs.getString("product_id"),
                    entitlement = rs.getString("entitlement"),
                    currentPeriodEnd = rs.getTimestamp("current_period_end")?.toInstant(),
                )
            },
            status,
            status,
            safeSize,
            safePage * safeSize,
        )
    }

    private companion object {
        const val MAX_PAGE_SIZE = 200
    }
}
