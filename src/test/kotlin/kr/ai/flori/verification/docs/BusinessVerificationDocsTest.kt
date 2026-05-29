package kr.ai.flori.verification.docs

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
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.presigner.S3Presigner

/**
 * 사업자 인증 API RestDocs 문서화. 실제 보안 체인 + Zonky PG에서 호출하며 OpenAPI 스펙 생성.
 * upload-target(presigned)은 더미 S3Presigner 빈으로 로컬 서명만 수행한다(네트워크 불요).
 * (게이팅 대상이 아니므로 인증 토큰만으로 호출 가능)
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest(
    properties = [
        "aws.s3.bucket=flori-test-bucket",
        "aws.cloudfront.domain=cdn.flori.dev",
        "aws.region=ap-northeast-2",
        "spring.main.allow-bean-definition-overriding=true",
    ],
)
@AutoConfigureMockMvc
@AutoConfigureRestDocs
class BusinessVerificationDocsTest : RestDocsSupport() {
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
                        AwsBasicCredentials.create("dummy-access-key-id", "dummy-secret-access-key"),
                    ),
                ).build()
    }

    @Test
    fun `등록증 업로드 타깃 발급 문서화`() {
        val token = signupAndToken()

        mockMvc
            .post("/verification/business/upload-target") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("contentType" to "image/jpeg"))
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "business-verification-upload-target",
                        tag = "BusinessVerification",
                        summary = "사업자등록증 업로드용 presigned PUT URL 발급",
                        requestFields =
                            listOf(
                                fieldWithPath("contentType")
                                    .type(JsonFieldType.STRING)
                                    .description("이미지/PDF MIME (image/jpeg·image/png·image/webp·application/pdf)"),
                            ),
                        responseFields =
                            listOf(
                                fieldWithPath("uploadUrl").type(JsonFieldType.STRING).description("presigned PUT URL"),
                                fieldWithPath("fileUrl").type(JsonFieldType.STRING).description("업로드 후 접근 URL(제출 시 사용)"),
                                fieldWithPath("expiresInSeconds").type(JsonFieldType.NUMBER).description("URL 만료(초)"),
                            ),
                    ),
                )
            }
    }

    @Test
    fun `사업자 인증 신청 문서화`() {
        val token = signupAndToken()
        // upload-target으로 본인 prefix 키의 fileUrl을 받아 그대로 제출(소유권 통과)
        val uploadJson =
            mockMvc
                .post("/verification/business/upload-target") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = json(mapOf("contentType" to "image/jpeg"))
                }.andReturn()
                .response.contentAsString
        val fileUrl = objectMapper.readTree(uploadJson)["fileUrl"].asText()

        mockMvc
            .post("/verification/business") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "businessNumber" to "1234567890",
                            "businessName" to "플로리 꽃집",
                            "representativeName" to "홍길동",
                            "businessLicenseUrl" to fileUrl,
                        ),
                    )
            }.andExpect { status { isCreated() } }
            .andDo {
                handle(
                    docs(
                        identifier = "business-verification-submit",
                        tag = "BusinessVerification",
                        summary = "사업자 인증 신청(PENDING 생성 + 관리자 Discord 알림)",
                        requestFields =
                            listOf(
                                fieldWithPath("businessNumber").type(JsonFieldType.STRING).description("사업자번호 숫자 10자리"),
                                fieldWithPath("businessName").type(JsonFieldType.STRING).description("상호"),
                                fieldWithPath("representativeName").type(JsonFieldType.STRING).description("대표자명"),
                                fieldWithPath("businessLicenseUrl").type(JsonFieldType.STRING).description("업로드한 등록증 URL(본인 키)"),
                            ),
                        responseFields =
                            listOf(
                                fieldWithPath("status").type(JsonFieldType.STRING).description("PENDING"),
                                fieldWithPath("rejectReason").type(JsonFieldType.STRING).optional().description("거절 사유(없으면 null)"),
                                fieldWithPath("submittedAt").type(JsonFieldType.STRING).optional().description("신청 시각"),
                                fieldWithPath("reviewedAt").type(JsonFieldType.STRING).optional().description("검토 시각(미검토 null)"),
                            ),
                    ),
                )
            }
    }

    @Test
    fun `사업자 인증 상태 조회 문서화`() {
        val token = signupAndToken()

        mockMvc
            .get("/verification/business/me") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "business-verification-me",
                        tag = "BusinessVerification",
                        summary = "내 사업자 인증 상태 조회(이력 없으면 NONE)",
                        responseFields =
                            listOf(
                                fieldWithPath("status")
                                    .type(JsonFieldType.STRING)
                                    .description("NONE | PENDING | APPROVED | REJECTED"),
                                fieldWithPath("rejectReason").type(JsonFieldType.STRING).optional().description("거절 사유"),
                                fieldWithPath("submittedAt").type(JsonFieldType.STRING).optional().description("신청 시각"),
                                fieldWithPath("reviewedAt").type(JsonFieldType.STRING).optional().description("검토 시각"),
                            ),
                    ),
                )
            }
    }
}
