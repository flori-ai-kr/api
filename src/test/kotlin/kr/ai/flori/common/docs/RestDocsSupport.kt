package kr.ai.flori.common.docs

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import com.epages.restdocs.apispec.Schema
import com.fasterxml.jackson.databind.ObjectMapper
import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.settings.entity.LabelDomains
import kr.ai.flori.settings.entity.LabelKinds
import kr.ai.flori.settings.repository.LabelSettingRepository
import kr.ai.flori.support.TestAccounts
import kr.ai.flori.verification.entity.BusinessVerification
import kr.ai.flori.verification.repository.BusinessVerificationRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.request.ParameterDescriptor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultHandler

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

    @Autowired
    protected lateinit var authService: AuthService

    @Autowired
    protected lateinit var tokenProvider: JwtTokenProvider

    @Autowired
    protected lateinit var businessVerificationRepository: BusinessVerificationRepository

    @Autowired
    protected lateinit var labelSettingRepository: LabelSettingRepository

    protected fun json(value: Any): String = objectMapper.writeValueAsString(value)

    /** 토큰 소유 유저의 시드된 라벨 설정 value → id. 매출/지출 요청 본문의 categoryId/channelId 용도. */
    protected fun labelId(
        token: String,
        domain: String,
        kind: String,
        value: String,
    ): Long {
        val userId = requireNotNull(tokenProvider.parse(token)).userId
        return requireNotNull(
            labelSettingRepository.findByUserIdAndDomainAndKindAndValue(userId, domain, kind, value),
        ).id!!
    }

    protected fun saleCategoryId(
        token: String,
        value: String = "basic_bouquet",
    ): Long = labelId(token, LabelDomains.SALE, LabelKinds.CATEGORY, value)

    protected fun saleChannelId(
        token: String,
        value: String = "kakaotalk",
    ): Long = labelId(token, LabelDomains.SALE, LabelKinds.CHANNEL, value)

    protected fun salePaymentId(
        token: String,
        value: String = "card",
    ): Long = labelId(token, LabelDomains.SALE, LabelKinds.PAYMENT, value)

    protected fun expenseCategoryId(
        token: String,
        value: String = "flower_purchase",
    ): Long = labelId(token, LabelDomains.EXPENSE, LabelKinds.CATEGORY, value)

    protected fun expensePaymentId(
        token: String,
        value: String = "card",
    ): Long = labelId(token, LabelDomains.EXPENSE, LabelKinds.PAYMENT, value)

    /**
     * 신규 소셜 가입을 완료하고 access 토큰을 발급한다(보호 엔드포인트 문서화용).
     * 실제 신규 경로(registerToken → register/complete)를 그대로 태워 User+프로필+기본 시드를 생성한다.
     */
    protected fun signupAndToken(): String = TestAccounts.register(authService, tokenProvider).accessToken

    /**
     * 가입 + 사업자 인증(APPROVED)까지 완료하고 access 토큰을 발급한다.
     * 커뮤니티 등 @RequiresBusinessVerified 게이팅 엔드포인트 문서화/테스트용.
     */
    protected fun signupVerifiedAndToken(): String {
        val tokenResponse = TestAccounts.register(authService, tokenProvider)
        val userId = tokenProvider.parse(tokenResponse.accessToken)!!.userId
        businessVerificationRepository.save(
            BusinessVerification(
                userId = userId,
                businessNumber = "1234567890",
                businessName = "테스트 가게",
                representativeName = "홍길동",
                businessLicenseUrl = "https://cdn.example.com/business-licenses/$userId/a.jpg",
            ).apply { approve() },
        )
        return tokenResponse.accessToken
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
     *   }.andDo { handle(docs(..., pathParameters = listOf(parameterWithName("id").description("고객 ID")))) }
     *
     * (RequestPostProcessor(`with{}`)로 세팅하면 @AutoConfigureRestDocs의 ConfigurerApplyingRequestPostProcessor가
     *  그보다 먼저 템플릿을 캡처해 null이 되므로, 반드시 `requestAttr`로 빌더에 직접 넣어 ID가 박히지 않게 한다.)
     *
     * `requestSchema`/`responseSchema`에 DTO 클래스명을 넘기면 OpenAPI `components.schemas`의 키가 그 이름이 된다
     * (= flori-ai/mobile orval 코드젠이 읽는 도메인 타입명). 미지정 시 restdocs-api-spec이 경로+해시 이름을 자동 생성하므로
     * 모바일이 읽는 계약에서는 **항상 명시**한다. 같은 이름 + 같은 필드 내용은 restdocs-api-spec이 한 스키마로 병합한다.
     * 배열(목록) 응답은 단건 객체 스키마를 `$ref`로 재사용하지 못하므로 `XxxListResponse`처럼 별도 이름을 부여한다.
     */
    protected fun docs(
        identifier: String,
        tag: String,
        summary: String,
        pathParameters: List<ParameterDescriptor> = emptyList(),
        requestFields: List<FieldDescriptor> = emptyList(),
        responseFields: List<FieldDescriptor> = emptyList(),
        requestSchema: String? = null,
        responseSchema: String? = null,
    ): ResultHandler {
        val params = ResourceSnippetParameters.builder().tag(tag).summary(summary)
        if (pathParameters.isNotEmpty()) params.pathParameters(*pathParameters.toTypedArray())
        if (requestFields.isNotEmpty()) params.requestFields(*requestFields.toTypedArray())
        if (responseFields.isNotEmpty()) params.responseFields(*responseFields.toTypedArray())
        if (requestSchema != null) params.requestSchema(Schema(requestSchema))
        if (responseSchema != null) params.responseSchema(Schema(responseSchema))
        return MockMvcRestDocumentationWrapper.document(identifier, snippets = arrayOf(resource(params.build())))
    }
}
