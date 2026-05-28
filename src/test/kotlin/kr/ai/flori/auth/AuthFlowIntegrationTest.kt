package kr.ai.flori.auth

import com.fasterxml.jackson.databind.ObjectMapper
import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.oauth.SocialOAuthClient
import kr.ai.flori.auth.oauth.SocialUserInfo
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

/**
 * 인증 전체 흐름(HTTP): 소셜 로그인(신규) → registerToken → 가입 완료 → 보호 엔드포인트 → refresh 회전 → 로그아웃.
 * 보안 필터 체인을 포함한 실제 컨텍스트 + 실제 PostgreSQL(Zonky)에서 검증한다.
 * 카카오 클라이언트는 매 호출마다 고유 신원을 반환하는 스텁으로 오버라이드한다.
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
@AutoConfigureMockMvc
@Import(AuthFlowIntegrationTest.StubSocialConfig::class)
@TestPropertySource(properties = ["spring.main.allow-bean-definition-overriding=true"])
class AuthFlowIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    /** 매 인증마다 고유 (provider, providerId)를 반환해 항상 "신규 신원"이 되게 하는 스텁. */
    @TestConfiguration
    class StubSocialConfig {
        @Bean("KAKAO")
        fun kakaoStub(): SocialOAuthClient =
            object : SocialOAuthClient {
                override fun authenticate(
                    code: String,
                    redirectUri: String,
                    state: String?,
                ): SocialUserInfo = SocialUserInfo("KAKAO", "kakao-${UUID.randomUUID()}", null, "카카오 사장님")
            }
    }

    private fun body(vararg pairs: Pair<String, Any?>) = objectMapper.writeValueAsString(pairs.toMap())

    /** 카카오 신규 로그인 → registerToken 획득. */
    private fun kakaoRegisterToken(): String {
        val response =
            mockMvc
                .post("/auth/oauth/kakao") {
                    contentType = MediaType.APPLICATION_JSON
                    content = body("code" to "code", "redirectUri" to "flori://oauth/kakao")
                }.andReturn()
                .response.contentAsString
        val json = objectMapper.readTree(response)
        check(!json.get("registered").asBoolean()) { "신규 신원이어야 함" }
        return json.get("registerToken").asText()
    }

    /** registerToken으로 가입 완료 → (access, refresh). */
    private fun completeRegister(registerToken: String): Pair<String, String> {
        val email = "flow-${UUID.randomUUID()}@flori.dev"
        val response =
            mockMvc
                .post("/auth/register/complete") {
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        body(
                            "registerToken" to registerToken,
                            "storeName" to "헤이즐 플라워",
                            "nickname" to "헤이즐",
                            "email" to email,
                            "regionSido" to "서울특별시",
                        )
                }.andReturn()
                .response.contentAsString
        val json = objectMapper.readTree(response)
        return json.get("accessToken").asText() to json.get("refreshToken").asText()
    }

    @Test
    fun `소셜로그인-가입완료-보호엔드포인트-refresh-로그아웃 전체 흐름`() {
        val (access, refresh) = completeRegister(kakaoRegisterToken())

        // 발급된 access로 보호 엔드포인트 접근 가능 + 가입 시 입력한 프로필 확인
        mockMvc
            .get("/me") { header(HttpHeaders.AUTHORIZATION, "Bearer $access") }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.profile.storeName") { value("헤이즐 플라워") } }

        // refresh 회전 → 새 토큰
        val refreshResponse =
            mockMvc
                .post("/auth/refresh") {
                    contentType = MediaType.APPLICATION_JSON
                    content = body("refreshToken" to refresh)
                }.andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString
        val newRefresh = objectMapper.readTree(refreshResponse).get("refreshToken").asText()

        // 회전된 옛 refresh는 거부
        mockMvc
            .post("/auth/refresh") {
                contentType = MediaType.APPLICATION_JSON
                content = body("refreshToken" to refresh)
            }.andExpect { status { isUnauthorized() } }

        // 로그아웃 후 새 refresh도 무효
        mockMvc
            .post("/auth/logout") {
                contentType = MediaType.APPLICATION_JSON
                content = body("refreshToken" to newRefresh)
            }.andExpect { status { isNoContent() } }

        mockMvc
            .post("/auth/refresh") {
                contentType = MediaType.APPLICATION_JSON
                content = body("refreshToken" to newRefresh)
            }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `신규 소셜 로그인은 registered=false와 registerToken을 준다`() {
        mockMvc
            .post("/auth/oauth/kakao") {
                contentType = MediaType.APPLICATION_JSON
                content = body("code" to "code", "redirectUri" to "flori://oauth/kakao")
            }.andExpect {
                status { isOk() }
                jsonPath("$.registered") { value(false) }
                jsonPath("$.registerToken") { isNotEmpty() }
                jsonPath("$.socialNickname") { value("카카오 사장님") }
            }
    }

    @Test
    fun `토큰 없이 보호 엔드포인트는 401`() {
        mockMvc.get("/me").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `잘못된 토큰으로 보호 엔드포인트는 401`() {
        mockMvc
            .get("/me") { header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.value") }
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `이미 가입된 신원의 registerToken 재사용은 409`() {
        val registerToken = kakaoRegisterToken()
        completeRegister(registerToken)

        // 같은 registerToken으로 다시 가입 완료 시도 → 이미 가입됨(409)
        mockMvc
            .post("/auth/register/complete") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    body(
                        "registerToken" to registerToken,
                        "storeName" to "다른 가게",
                        "nickname" to "다른 닉",
                        "email" to "dup-${UUID.randomUUID()}@flori.dev",
                        "regionSido" to "서울특별시",
                    )
            }.andExpect { status { isConflict() } }
    }

    @Test
    fun `필수 필드 누락 가입 완료는 400`() {
        mockMvc
            .post("/auth/register/complete") {
                contentType = MediaType.APPLICATION_JSON
                // storeName 누락
                content =
                    body(
                        "registerToken" to kakaoRegisterToken(),
                        "nickname" to "헤이즐",
                        "email" to "flow-${UUID.randomUUID()}@flori.dev",
                        "regionSido" to "서울특별시",
                    )
            }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `위조된 registerToken 가입 완료는 401`() {
        mockMvc
            .post("/auth/register/complete") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    body(
                        "registerToken" to "forged.invalid.token",
                        "storeName" to "헤이즐 플라워",
                        "nickname" to "헤이즐",
                        "email" to "flow-${UUID.randomUUID()}@flori.dev",
                        "regionSido" to "서울특별시",
                    )
            }.andExpect { status { isUnauthorized() } }
    }
}
