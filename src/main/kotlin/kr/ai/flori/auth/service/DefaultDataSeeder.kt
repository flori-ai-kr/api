package kr.ai.flori.auth.service

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * 가입 시 사용자별 기본 라벨 설정 시드(매출 카테고리/결제방식/채널, 지출 카테고리/결제방식).
 * 단일 테이블 label_settings에 (domain, kind)로 구분해 적재한다.
 * 모든 INSERT는 복합 unique(user_id, domain, kind, value) 기준 ON CONFLICT DO NOTHING으로 멱등하다(재시도 안전).
 *
 * 기본값 출처: 원본 flori-ai/web의 PRODUCT_CATEGORIES / 결제방식 / 채널 / expense-settings 기본값.
 */
@Component
class DefaultDataSeeder(
    private val jdbcTemplate: JdbcTemplate,
) {
    private data class Setting(
        val value: String,
        val label: String,
    )

    fun seedForNewUser(userId: Long) {
        seedLabels(userId, "sale", "category", SALE_CATEGORIES)
        seedLabels(userId, "sale", "payment", SALE_PAYMENT_METHODS)
        seedLabels(userId, "sale", "channel", SALE_CHANNELS)
        seedLabels(userId, "expense", "category", EXPENSE_CATEGORIES)
        seedLabels(userId, "expense", "payment", EXPENSE_PAYMENT_METHODS)
    }

    private fun seedLabels(
        userId: Long,
        domain: String,
        kind: String,
        rows: List<Setting>,
    ) {
        val sql =
            "INSERT INTO label_settings (user_id, domain, kind, value, label, sort_order) " +
                "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (user_id, domain, kind, value) DO NOTHING"
        val args =
            rows.mapIndexed { idx, row ->
                arrayOf<Any>(userId, domain, kind, row.value, row.label, idx + 1)
            }
        jdbcTemplate.batchUpdate(sql, args)
    }

    private companion object {
        val SALE_CATEGORIES =
            listOf(
                Setting("mini_bouquet", "미니 꽃다발"),
                Setting("basic_bouquet", "기본 꽃다발"),
                Setting("medium_bouquet", "중형 꽃다발"),
                Setting("large_bouquet", "대형 꽃다발"),
                Setting("special_bouquet", "스페셜 꽃다발"),
                Setting("proposal_bouquet", "프로포즈 꽃다발"),
                Setting("basket", "꽃바구니"),
                Setting("vase", "화병꽂이"),
                Setting("group_bouquet", "단체꽃다발"),
                Setting("reservation", "예약"),
                Setting("photo_bouquet", "촬영부케"),
            )

        val SALE_PAYMENT_METHODS =
            listOf(
                Setting("card", "카드"),
                Setting("naverpay", "네이버페이"),
                Setting("transfer", "계좌이체"),
                Setting("cash", "현금"),
            )

        val SALE_CHANNELS =
            listOf(
                Setting("phone", "전화"),
                Setting("kakaotalk", "카카오톡"),
                Setting("naver_booking", "네이버예약"),
                Setting("road", "로드"),
                Setting("other", "기타"),
            )

        val EXPENSE_CATEGORIES =
            listOf(
                Setting("flower_purchase", "꽃 사입"),
                Setting("delivery", "배송비"),
                Setting("advertising", "광고비"),
                Setting("rent", "임대료"),
                Setting("utilities", "공과금"),
                Setting("supplies", "소모품"),
                Setting("other", "기타"),
            )

        val EXPENSE_PAYMENT_METHODS =
            listOf(
                Setting("card", "카드"),
                Setting("cash", "현금"),
                Setting("transfer", "계좌이체"),
            )
    }
}
