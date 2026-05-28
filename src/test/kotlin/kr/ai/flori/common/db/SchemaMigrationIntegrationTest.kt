package kr.ai.flori.common.db

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

/**
 * 스키마 정본(docs/sql DDL)을 실제 PostgreSQL(Zonky 임베디드)에 적용해 검증한다.
 * - 모든 도메인 테이블 생성 확인
 * - 멀티테넌시 전제: RLS 정책 부재(애플리케이션이 격리 책임)
 * - user_id가 자체 users 테이블을 참조(원본 auth 스키마 제거)
 * - 공유 시드(instagram_accounts) 적재
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class SchemaMigrationIntegrationTest {
    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `docs sql DDL로 모든 도메인 테이블이 생성된다`() {
        val tables =
            jdbcTemplate
                .queryForList(
                    "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                    String::class.java,
                ).toSet()

        val expected =
            setOf(
                "users",
                "customers",
                "reservations",
                "sales",
                "expenses",
                "recurring_expenses",
                "recurring_skips",
                "sale_categories",
                "payment_methods",
                "expense_categories",
                "expense_payment_methods",
                "photo_tags",
                "photo_cards",
                "push_subscriptions",
                "calendar_events",
                "trend_articles",
                "instagram_accounts",
                "instagram_posts",
                "user_preferences",
                "insight_scraps",
            )
        assertThat(tables).containsAll(expected)
    }

    @Test
    fun `RLS 정책이 하나도 존재하지 않는다`() {
        val policyCount =
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_policies WHERE schemaname = 'public'",
                Long::class.java,
            )
        assertThat(policyCount).isZero()
    }

    @Test
    fun `user_id 외래키는 users 테이블을 참조한다`() {
        val userId =
            jdbcTemplate.queryForObject(
                "INSERT INTO users(email, nickname, provider, provider_id) " +
                    "VALUES ('tenant@flori.dev', 'tenant-fk-user', 'GOOGLE', 'tenant-fk-1') RETURNING id",
                Long::class.java,
            )

        jdbcTemplate.update(
            "INSERT INTO customers(user_id, name, phone) VALUES (?, '홍길동', '01000000000')",
            userId,
        )

        val count =
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM customers WHERE user_id = ?",
                Long::class.java,
                userId,
            )
        assertThat(count).isEqualTo(1)
    }

    @Test
    fun `존재하지 않는 user_id로는 행을 넣을 수 없다`() {
        val insert = {
            jdbcTemplate.update(
                "INSERT INTO customers(user_id, name, phone) VALUES (999999999, '없음', '01099999999')",
            )
        }
        assertThat(runCatching { insert() }.isFailure).isTrue()
    }

    @Test
    fun `공유 인스타그램 계정 시드가 적재된다`() {
        val count = jdbcTemplate.queryForObject("SELECT count(*) FROM instagram_accounts", Long::class.java)
        assertThat(count).isEqualTo(15)
    }
}
