package kr.ai.flori

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class FloriServerApplicationTests {
    @Test
    fun contextLoads() {
        // 임베디드 PostgreSQL + docs/sql DDL이 적용된 상태로 컨텍스트가 로딩되는지 검증한다.
    }
}
