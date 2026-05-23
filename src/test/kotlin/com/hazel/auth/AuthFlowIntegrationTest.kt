package com.hazel.auth

import com.fasterxml.jackson.databind.ObjectMapper
import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

/**
 * 인증 전체 흐름(HTTP): 가입 → 보호 엔드포인트 접근 → refresh 회전 → 로그아웃.
 * 보안 필터 체인을 포함한 실제 컨텍스트 + 실제 PostgreSQL(Zonky)에서 검증.
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private fun uniqueEmail() = "flow-${UUID.randomUUID()}@hazel.dev"

    private fun body(vararg pairs: Pair<String, String>) = objectMapper.writeValueAsString(pairs.toMap())

    private fun signup(email: String): Pair<String, String> {
        val response =
            mockMvc
                .post("/auth/signup") {
                    contentType = MediaType.APPLICATION_JSON
                    content = body("email" to email, "password" to "password123", "name" to "사장님")
                }.andReturn()
                .response.contentAsString
        val json = objectMapper.readTree(response)
        return json.get("accessToken").asText() to json.get("refreshToken").asText()
    }

    @Test
    fun `가입-보호엔드포인트-refresh-로그아웃 전체 흐름`() {
        val email = uniqueEmail()
        val (access, refresh) = signup(email)

        // 발급된 access로 보호 엔드포인트 접근 가능
        mockMvc
            .get("/me") { header(HttpHeaders.AUTHORIZATION, "Bearer $access") }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.email") { value(email) } }

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
    fun `중복 이메일 가입은 409`() {
        val email = uniqueEmail()
        signup(email)
        mockMvc
            .post("/auth/signup") {
                contentType = MediaType.APPLICATION_JSON
                content = body("email" to email, "password" to "password123")
            }.andExpect { status { isConflict() } }
    }

    @Test
    fun `짧은 비밀번호 가입은 400`() {
        mockMvc
            .post("/auth/signup") {
                contentType = MediaType.APPLICATION_JSON
                content = body("email" to uniqueEmail(), "password" to "short")
            }.andExpect { status { isBadRequest() } }
    }
}
