package kr.ai.flori.common.db

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/**
 * DB 없이 스키마 정본 SQL(docs/sql/all-tables-ddl.sql)의 이식 규칙을 검증한다(환경 무관, 항상 실행).
 * 핵심 보안 전제(멀티테넌시·RLS 제거·자체 인증)가 회귀하지 않도록 잠근다.
 */
class MigrationRulesTest {
    private val sql: String =
        File("docs/sql/all-tables-ddl.sql")
            .also { require(it.exists()) { "스키마 정본 DDL을 찾을 수 없습니다: ${it.absolutePath}" } }
            .readText()

    @Test
    fun `Supabase RLS 정책 구문이 없다`() {
        assertThat(sql).doesNotContain("ENABLE ROW LEVEL SECURITY")
        assertThat(sql).doesNotContain("CREATE POLICY")
        assertThat(sql).doesNotContain("auth.uid()")
    }

    @Test
    fun `auth 스키마 FK가 없고 자체 users 테이블을 참조한다`() {
        assertThat(sql).doesNotContain("auth.users")
        assertThat(sql).contains("CREATE TABLE users")
        assertThat(sql).contains("REFERENCES users(id)")
    }

    @Test
    fun `핵심 복합 unique 제약이 유지된다`() {
        assertThat(sql).contains("UNIQUE (phone, user_id)")
        assertThat(sql).contains("UNIQUE (value, user_id)")
        assertThat(sql).contains("UNIQUE (user_id, target_type, target_id)")
    }

    @Test
    fun `jsonb·배열 타입이 보존된다`() {
        assertThat(sql).contains("JSONB")
        assertThat(sql).contains("TEXT[]")
        assertThat(sql).contains("INTEGER[]")
    }
}
