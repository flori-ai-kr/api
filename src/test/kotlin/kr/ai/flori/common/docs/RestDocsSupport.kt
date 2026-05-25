package kr.ai.flori.common.docs

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import com.fasterxml.jackson.databind.ObjectMapper
import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.request.ParameterDescriptor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultHandler
import org.springframework.test.web.servlet.post
import java.util.UUID

/**
 * RestDocs 문서화 통합테스트 공용 베이스.
 *
 * 실제 보안 필터 체인 + 실제 PostgreSQL(Zonky)에서 엔드포인트를 호출하며 OpenAPI 스펙을 생성한다.
 * 문서는 어노테이션이 아니라 이 테스트가 단일 출처(SSOT)다.
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
abstract class RestDocsSupport {
    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    protected lateinit var objectMapper: ObjectMapper

    protected fun json(value: Any): String = objectMapper.writeValueAsString(value)

    /** 가입 후 access 토큰 발급(보호 엔드포인트 문서화용). */
    protected fun signupAndToken(): String {
        val res =
            mockMvc
                .post("/auth/signup") {
                    contentType = MediaType.APPLICATION_JSON
                    content = json(mapOf("email" to "docs-${UUID.randomUUID()}@flori.dev", "password" to "password123"))
                }.andReturn()
                .response.contentAsString
        val node = objectMapper.readTree(res)
        check(node.hasNonNull("accessToken")) { "signupAndToken 실패: 응답에 accessToken 없음. 응답=$res" }
        return node.get("accessToken").asText()
    }

    /**
     * Kotlin MockMvc DSL의 `andDo { handle(...) }`에 넣을 RestDocs 핸들러를 만든다.
     * ePages `resource(...)`로 태그·요약·경로/요청/응답 필드를 함께 기술해 OpenAPI를 풍부화한다.
     *
     * 경로 파라미터가 있으면 `pathParameters`를 넘기고, 요청 DSL 블록 안에서 URI 템플릿을 직접 세팅한다:
     *
     *   mockMvc.get("/customers/$id") {
     *       requestAttr(RestDocumentationGenerator.ATTRIBUTE_NAME_URL_TEMPLATE, "/customers/{id}")
     *       header(HttpHeaders.AUTHORIZATION, "Bearer $token")
     *   }.andDo { handle(docs(..., pathParameters = listOf(parameterWithName("id").description("고객 UUID")))) }
     *
     * (RequestPostProcessor(`with{}`)로 세팅하면 @AutoConfigureRestDocs의 ConfigurerApplyingRequestPostProcessor가
     *  그보다 먼저 템플릿을 캡처해 null이 되므로, 반드시 `requestAttr`로 빌더에 직접 넣어 UUID가 박히지 않게 한다.)
     */
    protected fun docs(
        identifier: String,
        tag: String,
        summary: String,
        pathParameters: List<ParameterDescriptor> = emptyList(),
        requestFields: List<FieldDescriptor> = emptyList(),
        responseFields: List<FieldDescriptor> = emptyList(),
    ): ResultHandler {
        val params = ResourceSnippetParameters.builder().tag(tag).summary(summary)
        if (pathParameters.isNotEmpty()) params.pathParameters(*pathParameters.toTypedArray())
        if (requestFields.isNotEmpty()) params.requestFields(*requestFields.toTypedArray())
        if (responseFields.isNotEmpty()) params.responseFields(*responseFields.toTypedArray())
        return MockMvcRestDocumentationWrapper.document(identifier, snippets = arrayOf(resource(params.build())))
    }
}
