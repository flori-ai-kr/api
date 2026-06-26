package kr.ai.flori.photos.docs

import kr.ai.flori.common.docs.RestDocsSupport
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.restdocs.generate.RestDocumentationGenerator
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post

/**
 * PhotoCards API RestDocs 문서화.
 * 실제 보안 체인 + Zonky PG에서 각 엔드포인트를 1회 호출하며 OpenAPI 스펙을 생성한다.
 *
 * S3 presigned 업로드 타깃 발급 엔드포인트(POST /{id}/upload-targets)는 더미 S3Presigner 빈이
 * 필요해 별도 테스트 클래스(PhotoUploadTargetsDocsTest)에서 문서화한다.
 */
class PhotoCardDocsTest : RestDocsSupport() {
    /** PhotoCardResponse 공통 응답 필드 — 단건/생성/수정에서 재사용 */
    private val photoCardResponseFields =
        listOf(
            fieldWithPath("id").type(JsonFieldType.NUMBER).description("사진 카드 ID"),
            fieldWithPath("title").type(JsonFieldType.STRING).description("카드 제목"),
            fieldWithPath("memo")
                .type(JsonFieldType.STRING)
                .optional()
                .description("카드 설명 (미입력이면 null)"),
            fieldWithPath("tags")
                .type(JsonFieldType.ARRAY)
                .description("태그 목록 (태그 이름 문자열 배열)"),
            fieldWithPath("photos")
                .type(JsonFieldType.ARRAY)
                .description("사진 목록 (url·originalName 포함, 최대 10장)"),
            fieldWithPath("photos[].url")
                .type(JsonFieldType.STRING)
                .optional()
                .description("사진 URL (CloudFront)"),
            fieldWithPath("photos[].originalName")
                .type(JsonFieldType.STRING)
                .optional()
                .description("원본 파일명"),
            fieldWithPath("photos[].size")
                .type(JsonFieldType.NUMBER)
                .optional()
                .description("파일 크기 (바이트, 미입력 시 0)"),
            fieldWithPath("saleId")
                .type(JsonFieldType.NUMBER)
                .optional()
                .description("연결된 매출 ID (미연결이면 null)"),
            fieldWithPath("customerId")
                .type(JsonFieldType.NUMBER)
                .optional()
                .description("직접 연결된 고객 ID (미연결이면 null)"),
            fieldWithPath("customerName")
                .type(JsonFieldType.STRING)
                .optional()
                .description("연결된 고객 이름 (배지 표시용, 미연결/삭제 시 null)"),
            fieldWithPath("createdAt").type(JsonFieldType.STRING).description("생성 시각 (ISO-8601)"),
            fieldWithPath("updatedAt").type(JsonFieldType.STRING).description("최종 수정 시각 (ISO-8601)"),
        )

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
                                "title" to "결혼식 부케",
                                "memo" to "봄 웨딩 부케 작업",
                                "tags" to listOf("웨딩", "부케"),
                            ),
                        )
                }.andReturn()
                .response.contentAsString
        return objectMapper.readTree(res).get("id").asText()
    }

    // ── 1. 사진 카드 목록 조회 ─────────────────────────────────────────────────

    @Test
    fun `사진 카드 목록 조회 문서화`() {
        val token = signupAndToken()
        createPhotoCard(token)

        mockMvc
            .get("/photo-cards") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("tag", "웨딩")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "photo-card-list",
                        responseSchema = "PhotoCardsPageResponse",
                        tag = "PhotoCards",
                        summary = "사진 카드 목록 (커서 페이지네이션 + tag/customerId 필터)",
                        responseFields =
                            listOf(
                                fieldWithPath("cards")
                                    .type(JsonFieldType.ARRAY)
                                    .description("사진 카드 목록"),
                                fieldWithPath("cards[].id")
                                    .type(JsonFieldType.NUMBER)
                                    .description("사진 카드 ID"),
                                fieldWithPath("cards[].title")
                                    .type(JsonFieldType.STRING)
                                    .description("카드 제목"),
                                fieldWithPath("cards[].memo")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("카드 설명"),
                                fieldWithPath("cards[].tags")
                                    .type(JsonFieldType.ARRAY)
                                    .description("태그 목록"),
                                fieldWithPath("cards[].photos")
                                    .type(JsonFieldType.ARRAY)
                                    .description("사진 목록"),
                                fieldWithPath("cards[].saleId")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("연결된 매출 ID"),
                                fieldWithPath("cards[].customerId")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("직접 연결된 고객 ID"),
                                fieldWithPath("cards[].customerName")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("연결된 고객 이름"),
                                fieldWithPath("cards[].createdAt")
                                    .type(JsonFieldType.STRING)
                                    .description("생성 시각 (ISO-8601)"),
                                fieldWithPath("cards[].updatedAt")
                                    .type(JsonFieldType.STRING)
                                    .description("최종 수정 시각 (ISO-8601)"),
                                fieldWithPath("nextCursor")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("다음 페이지 커서 (updatedAt 기준 ISO-8601, 마지막 페이지이면 null)"),
                                fieldWithPath("hasMore")
                                    .type(JsonFieldType.BOOLEAN)
                                    .description("다음 페이지 존재 여부 (무한스크롤용)"),
                                fieldWithPath("totalCards")
                                    .type(JsonFieldType.NUMBER)
                                    .description("현재 필터 기준 전체 카드 수 (페이지 무관, 요약 헤더용)"),
                                fieldWithPath("totalPhotos")
                                    .type(JsonFieldType.NUMBER)
                                    .description("현재 필터 기준 전체 사진 장수 (페이지 무관, 요약 헤더용)"),
                            ),
                    ),
                )
            }
    }

    // ── 2. 사진 카드 생성 ──────────────────────────────────────────────────────

    @Test
    fun `사진 카드 생성 문서화`() {
        val token = signupAndToken()

        mockMvc
            .post("/photo-cards") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "title" to "가을 꽃다발",
                            "memo" to "국화 혼합 꽃다발",
                            "tags" to listOf("꽃다발", "가을"),
                            "photos" to
                                listOf(
                                    mapOf("url" to "https://cdn.example.com/photo1.jpg", "originalName" to "photo1.jpg"),
                                ),
                        ),
                    )
            }.andExpect { status { isCreated() } }
            .andDo {
                handle(
                    docs(
                        identifier = "photo-card-create",
                        requestSchema = "PhotoCardCreateRequest",
                        responseSchema = "PhotoCardResponse",
                        tag = "PhotoCards",
                        summary = "사진 카드 생성 (최대 10장, 태그 다중 지정 가능)",
                        requestFields =
                            listOf(
                                fieldWithPath("title")
                                    .type(JsonFieldType.STRING)
                                    .description("카드 제목 (필수)"),
                                fieldWithPath("memo")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("카드 설명"),
                                fieldWithPath("tags")
                                    .type(JsonFieldType.ARRAY)
                                    .optional()
                                    .description("태그 이름 목록 (빈 배열 허용)"),
                                fieldWithPath("photos")
                                    .type(JsonFieldType.ARRAY)
                                    .optional()
                                    .description("사진 목록 (최대 10장, 빈 배열 허용)"),
                                fieldWithPath("photos[].url")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("사진 URL"),
                                fieldWithPath("photos[].originalName")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("원본 파일명"),
                                fieldWithPath("photos[].size")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("파일 크기 (바이트, 미입력 시 0)"),
                                fieldWithPath("saleId")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("연결할 매출 ID (본인 소유 검증)"),
                                fieldWithPath("customerId")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("직접 연결할 고객 ID (본인 소유 검증)"),
                            ),
                        responseFields = photoCardResponseFields,
                    ),
                )
            }
    }

    // ── 3. 사진 카드 단건 조회 ─────────────────────────────────────────────────

    @Test
    fun `사진 카드 단건 조회 문서화`() {
        val token = signupAndToken()
        val id = createPhotoCard(token)

        mockMvc
            .get("/photo-cards/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/photo-cards/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "photo-card-get",
                        responseSchema = "PhotoCardResponse",
                        tag = "PhotoCards",
                        summary = "사진 카드 단건 조회",
                        pathParameters = listOf(parameterWithName("id").description("사진 카드 ID")),
                        responseFields = photoCardResponseFields,
                    ),
                )
            }
    }

    // ── 4. 매출별 사진 카드 조회 ───────────────────────────────────────────────

    @Test
    fun `매출별 사진 카드 조회 문서화 - 없으면 204`() {
        val token = signupAndToken()

        // 매출 생성
        val saleRes =
            mockMvc
                .post("/sales") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        json(
                            mapOf(
                                "date" to "2026-05-22",
                                "categoryId" to saleCategoryId(token),
                                "amount" to 50_000,
                                "paymentMethodId" to salePaymentId(token),
                            ),
                        )
                }.andReturn()
                .response.contentAsString
        val saleId = objectMapper.readTree(saleRes).get("id").asText()

        // 연결된 사진 카드가 없으므로 204 반환
        mockMvc
            .get("/photo-cards/by-sale/$saleId") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/photo-cards/by-sale/{saleId}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isNoContent() } }
            .andDo {
                handle(
                    docs(
                        identifier = "photo-card-by-sale-not-found",
                        tag = "PhotoCards",
                        summary = "매출별 사진 카드 조회 — 없음 (204 No Content)",
                        pathParameters = listOf(parameterWithName("saleId").description("매출 ID")),
                    ),
                )
            }
    }

    @Test
    fun `매출별 사진 카드 조회 문서화 - 있으면 200`() {
        val token = signupAndToken()

        // 매출 생성
        val saleRes =
            mockMvc
                .post("/sales") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        json(
                            mapOf(
                                "date" to "2026-05-22",
                                "categoryId" to saleCategoryId(token),
                                "amount" to 50_000,
                                "paymentMethodId" to salePaymentId(token),
                            ),
                        )
                }.andReturn()
                .response.contentAsString
        val saleId = objectMapper.readTree(saleRes).get("id").asText()

        // 매출 연결한 사진 카드 생성
        mockMvc
            .post("/photo-cards") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("title" to "매출 연결 카드", "saleId" to saleId))
            }.andReturn()

        mockMvc
            .get("/photo-cards/by-sale/$saleId") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/photo-cards/by-sale/{saleId}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "photo-card-by-sale-found",
                        responseSchema = "PhotoCardResponse",
                        tag = "PhotoCards",
                        summary = "매출별 사진 카드 조회 — 있음 (200 + 카드 반환)",
                        pathParameters = listOf(parameterWithName("saleId").description("매출 ID")),
                        responseFields = photoCardResponseFields,
                    ),
                )
            }
    }

    // ── 5. 사진 카드 수정 ──────────────────────────────────────────────────────

    @Test
    fun `사진 카드 수정 문서화`() {
        val token = signupAndToken()
        val id = createPhotoCard(token)

        mockMvc
            .patch("/photo-cards/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/photo-cards/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "title" to "수정된 부케",
                            "tags" to listOf("웨딩", "봄", "수정"),
                        ),
                    )
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "photo-card-update",
                        requestSchema = "PhotoCardUpdateRequest",
                        responseSchema = "PhotoCardResponse",
                        tag = "PhotoCards",
                        summary = "사진 카드 수정 (제공된 필드만 반영)",
                        pathParameters = listOf(parameterWithName("id").description("사진 카드 ID")),
                        requestFields =
                            listOf(
                                fieldWithPath("title")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("카드 제목 변경"),
                                fieldWithPath("memo")
                                    .type(JsonFieldType.STRING)
                                    .optional()
                                    .description("카드 설명 변경"),
                                fieldWithPath("tags")
                                    .type(JsonFieldType.ARRAY)
                                    .optional()
                                    .description("태그 목록 변경 (전체 교체)"),
                                fieldWithPath("photos")
                                    .type(JsonFieldType.ARRAY)
                                    .optional()
                                    .description("사진 목록 변경 (최대 10장, 전체 교체)"),
                                fieldWithPath("saleId")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("연결 매출 ID 변경"),
                                fieldWithPath("customerId")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("직접 연결 고객 ID 변경 (본인 소유 검증, null=미변경)"),
                                fieldWithPath("clearCustomer")
                                    .type(JsonFieldType.BOOLEAN)
                                    .optional()
                                    .description("true이면 고객 연결 해제 (customerId 무시)"),
                            ),
                        responseFields = photoCardResponseFields,
                    ),
                )
            }
    }

    // ── 6. 사진 순서 변경 ──────────────────────────────────────────────────────

    @Test
    fun `사진 순서 변경 문서화`() {
        val token = signupAndToken()

        // 사진 2장 포함한 카드 생성
        val res =
            mockMvc
                .post("/photo-cards") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        json(
                            mapOf(
                                "title" to "순서변경 테스트",
                                "photos" to
                                    listOf(
                                        mapOf("url" to "https://cdn.example.com/a.jpg", "originalName" to "a.jpg"),
                                        mapOf("url" to "https://cdn.example.com/b.jpg", "originalName" to "b.jpg"),
                                    ),
                            ),
                        )
                }.andReturn()
                .response.contentAsString
        val id = objectMapper.readTree(res).get("id").asText()

        mockMvc
            .patch("/photo-cards/$id/photos/reorder") {
                requestAttr(
                    RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE,
                    "/photo-cards/{id}/photos/reorder",
                )
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    json(
                        mapOf(
                            "photos" to
                                listOf(
                                    mapOf("url" to "https://cdn.example.com/b.jpg", "originalName" to "b.jpg"),
                                    mapOf("url" to "https://cdn.example.com/a.jpg", "originalName" to "a.jpg"),
                                ),
                        ),
                    )
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "photo-card-reorder",
                        requestSchema = "ReorderPhotosRequest",
                        responseSchema = "PhotoCardResponse",
                        tag = "PhotoCards",
                        summary = "사진 순서 변경 (photos 배열 전체를 원하는 순서로 전달)",
                        pathParameters = listOf(parameterWithName("id").description("사진 카드 ID")),
                        requestFields =
                            listOf(
                                fieldWithPath("photos")
                                    .type(JsonFieldType.ARRAY)
                                    .description("새로운 순서의 사진 목록 (필수)"),
                                fieldWithPath("photos[].url")
                                    .type(JsonFieldType.STRING)
                                    .description("사진 URL"),
                                fieldWithPath("photos[].originalName")
                                    .type(JsonFieldType.STRING)
                                    .description("원본 파일명"),
                                fieldWithPath("photos[].size")
                                    .type(JsonFieldType.NUMBER)
                                    .optional()
                                    .description("파일 크기 (바이트, 미입력 시 0)"),
                            ),
                        responseFields = photoCardResponseFields,
                    ),
                )
            }
    }

    // ── 7. 사진 1장 삭제 ───────────────────────────────────────────────────────

    @Test
    fun `사진 1장 삭제 문서화`() {
        val token = signupAndToken()
        val photoUrl = "https://cdn.example.com/del.jpg"

        // 사진 포함한 카드 생성
        val res =
            mockMvc
                .post("/photo-cards") {
                    header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        json(
                            mapOf(
                                "title" to "사진삭제 테스트",
                                "photos" to
                                    listOf(
                                        mapOf("url" to photoUrl, "originalName" to "del.jpg"),
                                    ),
                            ),
                        )
                }.andReturn()
                .response.contentAsString
        val id = objectMapper.readTree(res).get("id").asText()

        mockMvc
            .delete("/photo-cards/$id/photos") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/photo-cards/{id}/photos")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                param("url", photoUrl)
            }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "photo-card-delete-photo",
                        requestSchema = "EmptyRequest",
                        responseSchema = "PhotoCardResponse",
                        tag = "PhotoCards",
                        summary = "사진 1장 삭제 (url 쿼리 파라미터로 대상 지정)",
                        pathParameters = listOf(parameterWithName("id").description("사진 카드 ID")),
                        responseFields = photoCardResponseFields,
                    ),
                )
            }
    }

    // ── 8. 사진 카드 삭제 ──────────────────────────────────────────────────────

    @Test
    fun `사진 카드 삭제 문서화`() {
        val token = signupAndToken()
        val id = createPhotoCard(token)

        mockMvc
            .delete("/photo-cards/$id") {
                requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/photo-cards/{id}")
                header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }.andExpect { status { isNoContent() } }
            .andDo {
                handle(
                    docs(
                        identifier = "photo-card-delete",
                        tag = "PhotoCards",
                        summary = "사진 카드 삭제 (DB + 연결된 S3 객체까지 삭제)",
                        pathParameters = listOf(parameterWithName("id").description("사진 카드 ID")),
                    ),
                )
            }
    }
}
