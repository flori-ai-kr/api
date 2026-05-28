package kr.ai.flori.sales

import com.fasterxml.jackson.databind.ObjectMapper
import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.support.TestAccounts
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/**
 * 매출 API HTTP 흐름 + 멀티테넌시 격리(실제 보안필터·실제 PostgreSQL).
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
@AutoConfigureMockMvc
class SaleApiIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var tokenProvider: JwtTokenProvider

    private fun signupToken(): String = TestAccounts.register(authService, tokenProvider).accessToken

    private fun createCardSale(token: String): String {
        val response =
            mockMvc
                .post("/sales") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        objectMapper.writeValueAsString(
                            mapOf(
                                "date" to "2026-05-22",
                                "productCategory" to "basic_bouquet",
                                "amount" to 100_000,
                                "paymentMethod" to "card",
                            ),
                        )
                }.andReturn()
                .response.contentAsString
        return objectMapper.readTree(response).get("id").asText()
    }

    @Test
    fun `매출 생성 후 목록에 노출된다`() {
        val token = signupToken()

        mockMvc
            .post("/sales") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    objectMapper.writeValueAsString(
                        mapOf(
                            "date" to "2026-05-22",
                            "productCategory" to "basic_bouquet",
                            "amount" to 100_000,
                            "paymentMethod" to "card",
                        ),
                    )
            }.andExpect {
                status { isCreated() }
                jsonPath("$.paymentMethod") { value("card") }
            }

        mockMvc
            .get("/sales") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect {
                status { isOk() }
                jsonPath("$.sales.length()") { value(1) }
                jsonPath("$.hasMore") { value(false) }
            }
    }

    @Test
    fun `다른 사용자의 매출은 조회되지 않는다`() {
        val ownerToken = signupToken()
        val saleId = createCardSale(ownerToken)

        val otherToken = signupToken()
        mockMvc
            .get("/sales/$saleId") { header(HttpHeaders.AUTHORIZATION, "Bearer $otherToken") }
            .andExpect { status { isNotFound() } }

        mockMvc
            .get("/sales") { header(HttpHeaders.AUTHORIZATION, "Bearer $otherToken") }
            .andExpect {
                status { isOk() }
                jsonPath("$.sales.length()") { value(0) }
            }
    }

    @Test
    fun `토큰 없이 매출 API는 401`() {
        mockMvc.get("/sales").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `잘못된 입력은 400`() {
        val token = signupToken()
        mockMvc
            .post("/sales") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(mapOf("productCategory" to "x"))
            }.andExpect { status { isBadRequest() } }
    }
}
