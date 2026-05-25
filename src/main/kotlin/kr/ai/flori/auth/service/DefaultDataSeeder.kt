package kr.ai.flori.auth.service

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.UUID

/**
 * 가입 시 사용자별 기본 설정 시드(매출 카테고리/결제방식, 지출 카테고리/결제방식, 카드사).
 * 모든 INSERT는 복합 unique 기준 ON CONFLICT DO NOTHING으로 멱등하다(재시도 안전).
 *
 * 기본값 출처: 원본 flori-ai/web의 PRODUCT_CATEGORIES / 결제방식 / expense-settings 기본값.
 * 카드사는 원본에 기본값이 없어 국내 주요 발급사를 표준값(수수료 2.0%, 입금 3영업일)으로 시드한다.
 */
@Component
class DefaultDataSeeder(
    private val jdbcTemplate: JdbcTemplate,
) {
    private data class Setting(
        val value: String,
        val label: String,
        val color: String,
    )

    fun seedForNewUser(userId: UUID) {
        seedValueLabelTable("sale_categories", userId, SALE_CATEGORIES)
        seedValueLabelTable("payment_methods", userId, SALE_PAYMENT_METHODS)
        seedValueLabelTable("expense_categories", userId, EXPENSE_CATEGORIES)
        seedValueLabelTable("expense_payment_methods", userId, EXPENSE_PAYMENT_METHODS)
        seedCardCompanies(userId)
    }

    private fun seedValueLabelTable(
        table: String,
        userId: UUID,
        rows: List<Setting>,
    ) {
        // 방어적 가드: 테이블명은 SQL 바인딩 불가하므로 허용 목록으로만 보간(인젝션 차단).
        require(table in ALLOWED_TABLES) { "허용되지 않은 테이블: $table" }
        val sql =
            "INSERT INTO $table (user_id, value, label, color, sort_order) " +
                "VALUES (?, ?, ?, ?, ?) ON CONFLICT (value, user_id) DO NOTHING"
        val args =
            rows.mapIndexed { idx, row ->
                arrayOf<Any>(userId, row.value, row.label, row.color, idx + 1)
            }
        jdbcTemplate.batchUpdate(sql, args)
    }

    private fun seedCardCompanies(userId: UUID) {
        val sql =
            "INSERT INTO card_company_settings (user_id, name, fee_rate, deposit_days) " +
                "VALUES (?, ?, ?, ?) ON CONFLICT (name, user_id) DO NOTHING"
        val args =
            DEFAULT_CARD_COMPANIES.map { name ->
                arrayOf<Any>(userId, name, DEFAULT_FEE_RATE, DEFAULT_DEPOSIT_DAYS)
            }
        jdbcTemplate.batchUpdate(sql, args)
    }

    private companion object {
        val DEFAULT_FEE_RATE: BigDecimal = BigDecimal("2.0")
        const val DEFAULT_DEPOSIT_DAYS = 3
        val ALLOWED_TABLES =
            setOf("sale_categories", "payment_methods", "expense_categories", "expense_payment_methods")

        val SALE_CATEGORIES =
            listOf(
                Setting("mini_bouquet", "미니 꽃다발", "#f43f5e"),
                Setting("basic_bouquet", "기본 꽃다발", "#f43f5e"),
                Setting("medium_bouquet", "중형 꽃다발", "#f43f5e"),
                Setting("large_bouquet", "대형 꽃다발", "#f43f5e"),
                Setting("special_bouquet", "스페셜 꽃다발", "#f43f5e"),
                Setting("proposal_bouquet", "프로포즈 꽃다발", "#f43f5e"),
                Setting("basket", "꽃바구니", "#f43f5e"),
                Setting("vase", "화병꽂이", "#f43f5e"),
                Setting("group_bouquet", "단체꽃다발", "#f43f5e"),
                Setting("reservation", "예약", "#f43f5e"),
                Setting("photo_bouquet", "촬영부케", "#f43f5e"),
            )

        val SALE_PAYMENT_METHODS =
            listOf(
                Setting("card", "카드", "#3b82f6"),
                Setting("naverpay", "네이버페이", "#3b82f6"),
                Setting("transfer", "계좌이체", "#3b82f6"),
                Setting("cash", "현금", "#3b82f6"),
            )

        val EXPENSE_CATEGORIES =
            listOf(
                Setting("flower_purchase", "꽃 사입", "#ec4899"),
                Setting("delivery", "배송비", "#3b82f6"),
                Setting("advertising", "광고비", "#a855f7"),
                Setting("rent", "임대료", "#f97316"),
                Setting("utilities", "공과금", "#06b6d4"),
                Setting("supplies", "소모품", "#6b7280"),
                Setting("other", "기타", "#9ca3af"),
            )

        val EXPENSE_PAYMENT_METHODS =
            listOf(
                Setting("card", "카드", "#3b82f6"),
                Setting("cash", "현금", "#f97316"),
                Setting("transfer", "계좌이체", "#a855f7"),
            )

        val DEFAULT_CARD_COMPANIES =
            listOf(
                "신한카드",
                "삼성카드",
                "현대카드",
                "KB국민카드",
                "롯데카드",
                "BC카드",
                "하나카드",
                "우리카드",
                "NH농협카드",
            )
    }
}
