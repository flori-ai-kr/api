package kr.ai.flori.photos.docs

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.common.docs.RestDocsSupport
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.restdocs.generate.RestDocumentationGenerator
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.presigner.S3Presigner

/**
 * S3 presigned 업로드 타깃 발급 엔드포인트 RestDocs 문서화.
 * presigning은 로컬 서명 연산(네트워크 불요)이므로 더미 자격증명으로도 유효한 URL이 생성된다.
 * S3Presigner 빈을 테스트용 StaticCredentials로 오버라이드한다.
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest(
    properties = [
        "aws.s3.bucket=flori-test-bucket",
        "aws.cloudfront.domain=cdn.flori.dev",
        "aws.region=ap-northeast-2",
        // 테스트용 S3Presigner 빈이 애플리케이션 빈을 오버라이드하도록 허용
        "spring.main.allow-bean-definition-overriding=true",
    ],
)
@AutoConfigureMockMvc
@AutoConfigureRestDocs
class PhotoUploadTargetsDocsTest : RestDocsSupport() {
    /**
     * 테스트 전용 S3Presigner 빈.
     * 더미 자격증명으로 로컬 서명 수행 — 네트워크 호출 없이 유효한 presigned URL 생성.
     */
    @TestConfiguration
    @EnableConfigurationProperties
    class TestS3Config {
        @Bean
        @Primary
        fun s3Presigner(): S3Presigner =
            S3Presigner
                .builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(
                    StaticCredentialsProvider.create(
                        // 더미 자격증명 — 로컬 서명 전용 (실제 AWS 요청 없음)
                        AwsBasicCredentials.create("dummy-access-key-id", "dummy-secret-access-key"),
                    ),
                ).build()
    }

    /** 테스트용 사진 카드 생성 → 생성된 id 반환 */
    private fun createPhotoCard(token: String): String {
        val res =
            mockMvc
                .post("/photo-cards") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        json(
                            mapOf(
                                "title" to "업로드 테스트 카드",
                            ),
                        )
                }.andReturn()
                .response.contentAsString
        return objectMapper.readTree(res).get("id").asText()
    }

    /** 사진 1장이 포함된 카드 생성 → (id, photoUrl) 반환 */
    private fun createPhotoCardWithPhoto(token: String): Pair<String, String> {
        val photoUrl = "https://cdn.flori.dev/photo-cards/dl/${java.util.UUID.randomUUID()}.jpg"
        val res =
            mockMvc
                .post("/photo-cards") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        json(
                            mapOf(
                                "title" to "다운로드 테스트 카드",
                                "photos" to listOf(mapOf("url" to photoUrl, "originalName" to "flower.jpg")),
                            ),
                        )
                }.andReturn()
                .response.contentAsString
        return objectMapper.readTree(res).get("id").asText() to photoUrl
    }

    // ── 사진 업로드 타깃(presigned URL) 발급 ──────────────────────────────────

    @Test
    fun `사진 업로드 타깃 발급 문서화`() {
        val token = signupAndToken()
        val id = createPhotoCard(token)

        mockMvc
            .post("/photo-cards/$id/upload-targets") {
                requestAttr(
                    RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE,
                    "/photo-cards/{id}/upload-targets",
                )
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "files" to
                                listOf(
                                    mapOf(
                                        "name" to "flower.jpg",
                                        "type" to "image/jpeg",
                                        "size" to 102400,
                                    ),
                                ),
                        ),
                    )
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "photo-card-upload-targets",
                        tag = "PhotoCards",
                        summary = "사진 업로드 타깃 발급 (S3 presigned PUT URL + CloudFront 파일 URL)",
                        pathParameters = listOf(parameterWithName("id").description("사진 카드 ID")),
                        requestFields =
                            listOf(
                                fieldWithPath("files")
                                    .type(JsonFieldType.ARRAY)
                                    .description("업로드할 파일 메타 목록 (필수, 1개 이상)"),
                                fieldWithPath("files[].name")
                                    .type(JsonFieldType.STRING)
                                    .description("원본 파일명 (예: flower.jpg)"),
                                fieldWithPath("files[].type")
                                    .type(JsonFieldType.STRING)
                                    .description("MIME 타입 (이미지만 허용, 예: image/jpeg)"),
                                fieldWithPath("files[].size")
                                    .type(JsonFieldType.NUMBER)
                                    .description("파일 크기(byte)"),
                            ),
                        responseFields =
                            listOf(
                                fieldWithPath("[]")
                                    .type(JsonFieldType.ARRAY)
                                    .description("업로드 타깃 목록"),
                                fieldWithPath("[].uploadUrl")
                                    .type(JsonFieldType.STRING)
                                    .description("S3 presigned PUT URL (앱이 이 URL로 직접 업로드)"),
                                fieldWithPath("[].fileUrl")
                                    .type(JsonFieldType.STRING)
                                    .description("업로드 완료 후 조회에 사용할 CloudFront 파일 URL"),
                                fieldWithPath("[].originalName")
                                    .type(JsonFieldType.STRING)
                                    .description("원본 파일명"),
                            ),
                    ),
                )
            }
    }

    // ── 신규 카드용 업로드 타깃(카드 생성 전) ─────────────────────────────────

    @Test
    fun `신규 카드 업로드 타깃 발급 문서화`() {
        val token = signupAndToken()

        mockMvc
            .post("/photo-cards/upload-targets") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "files" to
                                listOf(
                                    mapOf("name" to "flower.jpg", "type" to "image/jpeg", "size" to 102400),
                                ),
                        ),
                    )
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "photo-card-upload-targets-new",
                        tag = "PhotoCards",
                        summary = "신규 카드용 업로드 타깃 발급 (카드 생성 전 — 업로드 성공 후 카드 생성). presigned PUT URL",
                        requestFields =
                            listOf(
                                fieldWithPath("files").type(JsonFieldType.ARRAY).description("업로드할 파일 메타 목록(필수, 1개 이상)"),
                                fieldWithPath("files[].name").type(JsonFieldType.STRING).description("원본 파일명"),
                                fieldWithPath("files[].type").type(JsonFieldType.STRING).description("MIME 타입(이미지만 허용)"),
                                fieldWithPath("files[].size").type(JsonFieldType.NUMBER).description("파일 크기(byte)"),
                            ),
                        responseFields =
                            listOf(
                                fieldWithPath("[]").type(JsonFieldType.ARRAY).description("업로드 타깃 목록"),
                                fieldWithPath("[].uploadUrl").type(JsonFieldType.STRING).description("S3 presigned PUT URL"),
                                fieldWithPath("[].fileUrl").type(JsonFieldType.STRING).description("업로드 후 조회용 CloudFront URL"),
                                fieldWithPath("[].originalName").type(JsonFieldType.STRING).description("원본 파일명"),
                            ),
                    ),
                )
            }
    }

    // ── 사진 원본 다운로드 (presigned GET) ────────────────────────────────────

    @Test
    fun `사진 다운로드 URL 발급 문서화`() {
        val token = signupAndToken()
        val (id, photoUrl) = createPhotoCardWithPhoto(token)

        mockMvc
            .get("/photo-cards/$id/photos/download") {
                requestAttr(
                    RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE,
                    "/photo-cards/{id}/photos/download",
                )
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("url", photoUrl)
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "photo-card-download",
                        tag = "PhotoCards",
                        summary = "사진 원본 다운로드 URL 발급 (presigned GET, 소유권 검증)",
                        pathParameters = listOf(parameterWithName("id").description("사진 카드 ID")),
                        responseFields =
                            listOf(
                                fieldWithPath("downloadUrl")
                                    .type(JsonFieldType.STRING)
                                    .description("원본 다운로드용 presigned GET URL (짧은 만료)"),
                            ),
                    ),
                )
            }
    }
}
