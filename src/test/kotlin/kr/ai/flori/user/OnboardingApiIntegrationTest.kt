package kr.ai.flori.user

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
 * 가게 프로필 편집 API(POST /me/profile) 통합테스트.
 *
 * 소셜 전용 전환 후 가입은 register/complete에서 끝나므로(가입 시 온보딩 완료), 이 엔드포인트는
 * 기존 사용자의 프로필 편집(upsert) 용도다. 실제 보안 체인 + Zonky PG에서 전체/최소 제출, 멱등 재제출,
 * /me 프로필 노출, 멀티테넌시 격리를 검증한다.
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
@AutoConfigureMockMvc
class OnboardingApiIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var tokenProvider: JwtTokenProvider

    /** 신규 가입 사용자 토큰(가입 시 기본 프로필이 이미 생성된 상태). */
    private fun token(): String = TestAccounts.register(authService, tokenProvider).accessToken

    @Test
    fun `가입 직후 me는 가입 시 입력한 프로필이 보인다`() {
        val token = token()
        mockMvc
            .get("/me") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect {
                status { isOk() }
                jsonPath("$.profile.storeName") { value("테스트 가게") }
            }
    }

    @Test
    fun `전체 프로필 편집 후 갱신된 프로필이 노출된다`() {
        val token = token()
        mockMvc
            .post("/me/profile") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    objectMapper.writeValueAsString(
                        mapOf(
                            "name" to "헤이즐 플라워",
                            "regionSido" to "서울특별시",
                            "regionSigungu" to "강남구",
                            "ownerAgeRange" to "30대",
                            "interests" to listOf("웨딩", "개업화환"),
                            "specialties" to listOf("꽃다발", "화분"),
                        ),
                    )
            }.andExpect {
                status { isOk() }
                jsonPath("$.profile.storeName") { value("헤이즐 플라워") }
                jsonPath("$.profile.regionSido") { value("서울특별시") }
                jsonPath("$.profile.regionSigungu") { value("강남구") }
                jsonPath("$.profile.ownerAgeRange") { value("30대") }
                jsonPath("$.profile.interests.length()") { value(2) }
                jsonPath("$.profile.specialties.length()") { value(2) }
            }

        mockMvc
            .get("/me") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect {
                status { isOk() }
                jsonPath("$.profile.storeName") { value("헤이즐 플라워") }
                jsonPath("$.profile.interests.length()") { value(2) }
            }
    }

    @Test
    fun `최소 편집 - name+regionSido만으로 성공하고 선택 필드는 비워진다`() {
        val token = token()
        mockMvc
            .post("/me/profile") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    objectMapper.writeValueAsString(
                        mapOf("name" to "미니 플라워", "regionSido" to "부산광역시"),
                    )
            }.andExpect {
                status { isOk() }
                jsonPath("$.profile.storeName") { value("미니 플라워") }
                jsonPath("$.profile.regionSigungu") { doesNotExist() }
                jsonPath("$.profile.ownerAgeRange") { doesNotExist() }
                jsonPath("$.profile.interests.length()") { value(0) }
                jsonPath("$.profile.specialties.length()") { value(0) }
            }
    }

    @Test
    fun `재편집은 멱등 - 프로필을 덮어쓰고 중복 행을 만들지 않는다`() {
        val token = token()

        fun submit(name: String) =
            mockMvc
                .post("/me/profile") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        objectMapper.writeValueAsString(
                            mapOf("name" to name, "regionSido" to "서울특별시"),
                        )
                }

        submit("첫번째 가게").andExpect { status { isOk() } }
        submit("바뀐 가게").andExpect {
            status { isOk() }
            jsonPath("$.profile.storeName") { value("바뀐 가게") }
        }

        mockMvc
            .get("/me") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect {
                status { isOk() }
                jsonPath("$.profile.storeName") { value("바뀐 가게") }
            }
    }

    @Test
    fun `name 누락 시 400`() {
        val token = token()
        mockMvc
            .post("/me/profile") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(mapOf("regionSido" to "서울특별시"))
            }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `regionSido 공백 시 400`() {
        val token = token()
        mockMvc
            .post("/me/profile") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(mapOf("name" to "가게", "regionSido" to "   "))
            }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `멀티테넌시 - 사용자 A의 프로필 편집이 사용자 B에게 노출되지 않는다`() {
        val tokenA = token()
        val tokenB = token()

        mockMvc
            .post("/me/profile") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $tokenA")
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(mapOf("name" to "A의 가게", "regionSido" to "서울특별시"))
            }.andExpect { status { isOk() } }

        // B는 자신의 기본 프로필만 본다(A의 변경이 보이지 않음)
        mockMvc
            .get("/me") { header(HttpHeaders.AUTHORIZATION, "Bearer $tokenB") }
            .andExpect {
                status { isOk() }
                jsonPath("$.profile.storeName") { value("테스트 가게") }
            }

        // A는 자신의 변경을 본다
        mockMvc
            .get("/me") { header(HttpHeaders.AUTHORIZATION, "Bearer $tokenA") }
            .andExpect {
                status { isOk() }
                jsonPath("$.profile.storeName") { value("A의 가게") }
            }
    }

    @Test
    fun `토큰 없이 프로필 편집은 401`() {
        mockMvc
            .post("/me/profile") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(mapOf("name" to "가게", "regionSido" to "서울특별시"))
            }.andExpect { status { isUnauthorized() } }
    }
}
