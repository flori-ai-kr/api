package kr.ai.flori.common

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.options

/**
 * 보안 헤더 + CORS가 실제 보안필터 체인을 통해 적용되는지 검증.
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
@AutoConfigureMockMvc
class InfraSecurityIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `보안 헤더가 응답에 포함된다`() {
        mockMvc.get("/health").andExpect {
            status { isOk() }
            header { string("X-Content-Type-Options", "nosniff") }
            header { string("X-Frame-Options", "DENY") }
            header { exists("Referrer-Policy") }
        }
    }

    @Test
    fun `허용 origin의 CORS 프리플라이트가 통과한다`() {
        mockMvc
            .options("/auth/oauth/kakao") {
                header("Origin", "http://localhost:3100")
                header("Access-Control-Request-Method", "POST")
            }.andExpect {
                status { isOk() }
                header { string("Access-Control-Allow-Origin", "http://localhost:3100") }
            }
    }
}
