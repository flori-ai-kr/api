package kr.ai.flori.sales

import com.fasterxml.jackson.databind.ObjectMapper
import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.settings.entity.LabelDomains
import kr.ai.flori.settings.entity.LabelKinds
import kr.ai.flori.settings.repository.LabelSettingRepository
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

    @Autowired
    lateinit var labelSettingRepository: LabelSettingRepository

    private fun signupToken(): String = TestAccounts.register(authService, tokenProvider).accessToken

    /** 토큰 소유 유저의 시드된 매출 카테고리 value → id. */
    private fun categoryId(
        token: String,
        value: String = "basic_bouquet",
    ): Long = labelId(token, LabelKinds.CATEGORY, value)

    /** 토큰 소유 유저의 시드된 매출 결제수단 value → id. */
    private fun paymentId(
        token: String,
        value: String = "card",
    ): Long = labelId(token, LabelKinds.PAYMENT, value)

    private fun labelId(
        token: String,
        kind: String,
        value: String,
    ): Long {
        val userId = requireNotNull(tokenProvider.parse(token)).userId
        return requireNotNull(
            labelSettingRepository.findByUserIdAndDomainAndKindAndValue(userId, LabelDomains.SALE, kind, value),
        ).id!!
    }

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
                                "categoryId" to categoryId(token),
                                "amount" to 100_000,
                                "paymentMethodId" to paymentId(token),
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
                            "categoryId" to categoryId(token),
                            "amount" to 100_000,
                            "paymentMethodId" to paymentId(token),
                        ),
                    )
            }.andExpect {
                status { isCreated() }
                jsonPath("$.paymentMethodId") { value(paymentId(token).toInt()) }
                jsonPath("$.isUnpaid") { value(false) }
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
    fun `과도하게 긴 자유입력(note)은 컨트롤러 경계에서 400으로 거부된다`() {
        val token = signupToken()

        mockMvc
            .post("/sales") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    objectMapper.writeValueAsString(
                        mapOf(
                            "date" to "2026-05-22",
                            "categoryId" to categoryId(token),
                            "amount" to 100_000,
                            "paymentMethodId" to paymentId(token),
                            "memo" to "가".repeat(201), // FieldLimits.MEMO(200) 초과
                        ),
                    )
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("E-CMN-001") }
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
                content = objectMapper.writeValueAsString(mapOf("amount" to 100_000))
            }.andExpect { status { isBadRequest() } }
    }
}
