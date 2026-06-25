package kr.ai.flori.ai

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.ai.client.AiBlogCall
import kr.ai.flori.ai.client.AiBlogDraft
import kr.ai.flori.ai.client.AiBlogFaq
import kr.ai.flori.ai.client.AiBlogResult
import kr.ai.flori.ai.client.AiBlogSection
import kr.ai.flori.ai.client.AiServerClient
import kr.ai.flori.ai.dto.BlogGenerateRequest
import kr.ai.flori.ai.dto.ToneProfileUpdateRequest
import kr.ai.flori.ai.entity.AiChatMessage
import kr.ai.flori.ai.entity.AiChatSession
import kr.ai.flori.ai.repository.AiChatMessageRepository
import kr.ai.flori.ai.repository.AiChatSessionRepository
import kr.ai.flori.ai.repository.AiMarketingContentRepository
import kr.ai.flori.ai.service.MarketingService
import kr.ai.flori.ai.service.PromptResolver
import kr.ai.flori.ai.service.ResolvedPrompt
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.support.TestAccounts
import kr.ai.flori.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.util.UUID

/**
 * AI 마케팅 게이트웨이 서비스 통합테스트. ai-server HTTP 호출([AiServerClient])만 [MockitoBean]으로 스텁하고,
 * tone_profile·콘텐츠 영속·store_context 조립·멀티테넌시는 실제 Zonky PG에서 검증한다.
 * 일일 캡은 5로 낮춰(아래 [TestPropertySource]) 캡 초과 경로를 적은 시드로 재현한다.
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
@TestPropertySource(properties = ["ai.gateway.usage-cap-per-day=5"])
class MarketingServiceTest {
    @Autowired
    lateinit var marketingService: MarketingService

    @MockitoBean
    lateinit var aiClient: AiServerClient

    @MockitoBean
    lateinit var promptResolver: PromptResolver

    @Autowired
    lateinit var authService: AuthService

    @Autowired
    lateinit var tokenProvider: JwtTokenProvider

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var contentRepository: AiMarketingContentRepository

    @Autowired
    lateinit var sessionRepository: AiChatSessionRepository

    @Autowired
    lateinit var messageRepository: AiChatMessageRepository

    @AfterEach
    fun tearDown() = TenantContext.clear()

    private fun newTenant(): Long {
        val email = "mkt-${UUID.randomUUID()}@flori.dev"
        TestAccounts.register(authService, tokenProvider, email)
        val userId = requireNotNull(userRepository.findByEmail(email)).id!!
        TenantContext.set(userId)
        return userId
    }

    // 비-널 Kotlin 파라미터용 any() 매처(mockito-kotlin 미사용). Mockito.any로 매처를 등록한 뒤
    // 제네릭 unchecked 캐스트로 null을 반환한다(제네릭 T라 Kotlin이 런타임 널 체크를 넣지 않음 → NPE 없음).
    @Suppress("UNCHECKED_CAST")
    private fun <T> uninitialized(): T = null as T

    private fun anyBlogCall(): AiBlogCall {
        Mockito.any(AiBlogCall::class.java)
        return uninitialized()
    }

    private fun captureBlog(captor: ArgumentCaptor<AiBlogCall>): AiBlogCall {
        captor.capture()
        return uninitialized()
    }

    private fun stubBlog() {
        Mockito
            .`when`(aiClient.generateBlog(anyString(), anyLong(), anyBlogCall()))
            .thenReturn(
                AiBlogResult(
                    draft =
                        AiBlogDraft(
                            title = "어버이날 카네이션 꽃다발 추천",
                            sections = listOf(AiBlogSection("어떤 꽃이 좋을까요?", "카네이션이 무난합니다.")),
                            faq = listOf(AiBlogFaq("당일배송 되나요?", "네 가능합니다.")),
                            hashtags = listOf("#어버이날꽃", "#카네이션"),
                        ),
                    model = "claude-haiku-4-5",
                    inputTokens = 100,
                    outputTokens = 200,
                ),
            )
    }

    @Test
    fun `blog 생성 - 초안을 반환하고 콘텐츠를 저장한다(토큰·모델 적재)`() {
        val userId = newTenant()
        stubBlog()

        val response = marketingService.generateBlog("jwt", BlogGenerateRequest(keyword = "어버이날 카네이션 꽃다발"))

        // 공개 계약은 문자열 ID(내부 entity.id를 toString)
        assertThat(response.contentId).isNotBlank()
        assertThat(response.draft.title).contains("어버이날")
        assertThat(response.draft.hashtags).contains("#어버이날꽃")

        val saved = contentRepository.findByIdAndUserIdAndDeletedAtIsNull(response.contentId.toLong(), userId)
        assertThat(saved).isNotNull
        assertThat(saved!!.channel).isEqualTo("blog")
        assertThat(saved.model).isEqualTo("claude-haiku-4-5")
        assertThat(saved.inputTokens).isEqualTo(100)
        assertThat(saved.outputJson.path("title").asText()).contains("어버이날")
    }

    @Test
    fun `blog 생성 - tone_profile·store_context를 조립해 ai-server에 전달한다`() {
        newTenant()
        stubBlog()
        marketingService.updateToneProfile(ToneProfileUpdateRequest(samples = listOf("안녕하세요 사장입니다.", "오늘도 예쁜 꽃 준비했어요.")))

        marketingService.generateBlog("jwt", BlogGenerateRequest(keyword = "장미 꽃다발", situation = "기념일", memo = "비누꽃도 추천"))

        val captor = ArgumentCaptor.forClass(AiBlogCall::class.java)
        Mockito.verify(aiClient).generateBlog(anyString(), anyLong(), captureBlog(captor))
        val call = captor.value
        assertThat(call.channel).isEqualTo("blog")
        assertThat(call.keyword).isEqualTo("장미 꽃다발")
        assertThat(call.situation).isEqualTo("기념일")
        assertThat(call.toneSamples).containsExactly("안녕하세요 사장입니다.", "오늘도 예쁜 꽃 준비했어요.")
        // store_context: register가 만든 가게 프로필(storeName="테스트 가게")이 shop_name으로 조립된다
        assertThat(call.storeContext).isNotNull
        assertThat(call.storeContext!!.shopName).isEqualTo("테스트 가게")
    }

    @Test
    fun `blog 생성 - active 프롬프트가 있으면 promptOverride를 ai-server에 주입한다`() {
        newTenant()
        stubBlog()
        Mockito
            .`when`(promptResolver.resolve("blog"))
            .thenReturn(
                ResolvedPrompt(
                    systemMd = "커스텀 시스템",
                    rulesMd = "커스텀 규칙",
                    outputSpecMd = "커스텀 스펙",
                    model = "claude-sonnet-4-6",
                    temperature = BigDecimal("0.70"),
                ),
            )

        marketingService.generateBlog("jwt", BlogGenerateRequest(keyword = "장미"))

        val captor = ArgumentCaptor.forClass(AiBlogCall::class.java)
        Mockito.verify(aiClient).generateBlog(anyString(), anyLong(), captureBlog(captor))
        val ov = captor.value.promptOverride
        assertThat(ov).isNotNull
        assertThat(ov!!.systemMd).isEqualTo("커스텀 시스템")
        assertThat(ov.rulesMd).isEqualTo("커스텀 규칙")
        assertThat(ov.outputSpecMd).isEqualTo("커스텀 스펙")
        assertThat(ov.model).isEqualTo("claude-sonnet-4-6")
        assertThat(ov.temperature!!).isEqualByComparingTo("0.70")
    }

    @Test
    fun `blog 생성 - active 프롬프트가 없으면 promptOverride 없이 호출한다(폴백)`() {
        newTenant()
        stubBlog()
        // promptResolver는 기본 mock — resolve("blog")가 null(폴백 신호)

        marketingService.generateBlog("jwt", BlogGenerateRequest(keyword = "장미"))

        val captor = ArgumentCaptor.forClass(AiBlogCall::class.java)
        Mockito.verify(aiClient).generateBlog(anyString(), anyLong(), captureBlog(captor))
        assertThat(captor.value.promptOverride).isNull()
    }

    @Test
    fun `blog 생성 - 일일 캡을 초과하면 403이고 ai-server를 호출하지 않는다`() {
        val userId = newTenant()
        // 캡(5)만큼 chat 메시지를 채운다(캡은 ai_chat_message 카운트 기준 — OCR/마케팅 공통).
        val session = sessionRepository.save(AiChatSession(userId, UUID.randomUUID().toString().replace("-", ""), "chat"))
        repeat(5) { messageRepository.save(AiChatMessage(session.id!!, userId, "user", "기존 $it")) }

        assertThatThrownBy { marketingService.generateBlog("jwt", BlogGenerateRequest(keyword = "꽃다발")) }
            .isInstanceOfSatisfying(AppException::class.java) {
                assertThat(it.errorCode).isEqualTo(CommonErrorCode.FORBIDDEN)
            }
        Mockito.verifyNoInteractions(aiClient)
    }

    @Test
    fun `tone_profile - upsert로 최대 3개까지 저장하고 조회된다`() {
        newTenant()
        marketingService.updateToneProfile(ToneProfileUpdateRequest(samples = listOf("샘플1", "샘플2", "샘플3", "샘플4")))

        val profile = marketingService.getToneProfile()
        assertThat(profile.samples).containsExactly("샘플1", "샘플2", "샘플3")

        // upsert: 같은 유저 재저장 시 덮어쓴다(중복 행 미생성)
        marketingService.updateToneProfile(ToneProfileUpdateRequest(samples = listOf("바뀐샘플")))
        assertThat(marketingService.getToneProfile().samples).containsExactly("바뀐샘플")
    }

    @Test
    fun `contents - 목록·상세·소프트삭제`() {
        newTenant()
        stubBlog()
        val created =
            marketingService.generateBlog(
                "jwt",
                BlogGenerateRequest(
                    keyword = "꽃바구니",
                    situation = "개업",
                    memo = "리본 추가",
                    photoUrls = listOf("https://cdn.example.com/p/1.jpg"),
                ),
            )
        val contentId = created.contentId.toLong()

        val list = marketingService.listContents("blog", 0, 20)
        val summary = list.contents.first { it.id == created.contentId }
        assertThat(summary.title).contains("어버이날")
        assertThat(summary.keyword).isEqualTo("꽃바구니")

        // 상세는 요약 필드 전부 + 입력 복원(situation/memo/photoUrls) + 초안
        val detail = marketingService.getContent(contentId)
        assertThat(detail.id).isEqualTo(created.contentId)
        assertThat(detail.keyword).isEqualTo("꽃바구니")
        assertThat(detail.situation).isEqualTo("개업")
        assertThat(detail.memo).isEqualTo("리본 추가")
        assertThat(detail.photoUrls).containsExactly("https://cdn.example.com/p/1.jpg")
        assertThat(detail.draft.sections).isNotEmpty

        marketingService.deleteContent(contentId)
        assertThat(marketingService.listContents("blog", 0, 20).contents.map { it.id }).doesNotContain(created.contentId)
        assertThatThrownBy { marketingService.getContent(contentId) }
            .isInstanceOfSatisfying(AppException::class.java) {
                assertThat(it.errorCode).isEqualTo(CommonErrorCode.NOT_FOUND)
            }
    }

    @Test
    fun `멀티테넌시 - 다른 테넌트의 콘텐츠는 조회·삭제할 수 없다`() {
        newTenant()
        stubBlog()
        val created = marketingService.generateBlog("jwt", BlogGenerateRequest(keyword = "소유자 콘텐츠"))
        val contentId = created.contentId.toLong()

        // 다른 사용자로 전환 후 같은 contentId 접근 → 404
        newTenant()
        assertThatThrownBy { marketingService.getContent(contentId) }
            .isInstanceOfSatisfying(AppException::class.java) {
                assertThat(it.errorCode).isEqualTo(CommonErrorCode.NOT_FOUND)
            }
        assertThatThrownBy { marketingService.deleteContent(contentId) }
            .isInstanceOfSatisfying(AppException::class.java) {
                assertThat(it.errorCode).isEqualTo(CommonErrorCode.NOT_FOUND)
            }
    }
}
