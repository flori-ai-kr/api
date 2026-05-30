package kr.ai.flori.admin.service

import kr.ai.flori.admin.dto.AdminUserPage
import kr.ai.flori.admin.dto.AdminUserRow
import kr.ai.flori.admin.error.AdminErrorCode
import kr.ai.flori.common.error.AppException
import kr.ai.flori.user.repository.UserRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 운영 콘솔 유저 조회/운영. cross-tenant — @RequiresAdmin 하위에서만 호출된다.
 * 목록은 JdbcTemplate 조인 projection(users ⨝ user_profiles ⨝ subscriptions ⨝ 최신 verification).
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
                size,
                page * size,
            )
        return AdminUserPage(rows, page, size, total)
    }

    @Transactional
    fun setActive(
        id: Long,
        active: Boolean,
    ): AdminUserRow {
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

    private companion object {
        val ROW_SELECT =
            """
            SELECT u.id, u.email, u.nickname, u.is_active, u.is_admin, u.created_at,
                   p.store_name,
                   s.status AS sub_status,
                   (SELECT bv.status FROM business_verifications bv
                      WHERE bv.user_id = u.id ORDER BY bv.created_at DESC LIMIT 1) AS verification_status
            FROM users u
            LEFT JOIN user_profiles p ON p.user_id = u.id
            LEFT JOIN subscriptions s ON s.user_id = u.id
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
                    subscriptionStatus = rs.getString("sub_status"),
                    verificationStatus = rs.getString("verification_status"),
                    createdAt = rs.getTimestamp("created_at")?.toInstant(),
                )
            }
    }
}
