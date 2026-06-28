package kr.ai.flori.ai.service

import kr.ai.flori.ai.entity.AiPrompt
import kr.ai.flori.ai.repository.AiPromptRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.math.BigDecimal

/**
 * PromptResolver 단위테스트(SPEC-AI-008). active 로드 + 5분 캐시 + 폴백(null) 동작을
 * mock 리포지토리 + 주입 clock으로 결정적으로 검증한다.
 */
class PromptResolverTest {
    private fun activePrompt(): AiPrompt =
        AiPrompt(channel = "blog", version = "v1", systemMd = "SYS").apply {
            isActive = true
            rulesMd = "RULES"
            outputSpecMd = "SPEC"
            model = "claude-sonnet-4-6"
            temperature = BigDecimal("0.70")
        }

    @Test
    fun `active 프롬프트가 있으면 ResolvedPrompt 를 반환한다`() {
        val repo = Mockito.mock(AiPromptRepository::class.java)
        Mockito.`when`(repo.findFirstByChannelAndIsActiveTrueAndDeletedAtIsNull("blog")).thenReturn(activePrompt())
        val resolver = PromptResolver(repo)

        val resolved = resolver.resolve("blog")

        assertThat(resolved).isNotNull
        assertThat(resolved!!.systemMd).isEqualTo("SYS")
        assertThat(resolved.rulesMd).isEqualTo("RULES")
        assertThat(resolved.outputSpecMd).isEqualTo("SPEC")
        assertThat(resolved.model).isEqualTo("claude-sonnet-4-6")
        assertThat(resolved.temperature!!).isEqualByComparingTo("0.70")
    }

    @Test
    fun `active 가 없으면 null 을 반환한다(폴백 신호)`() {
        val repo = Mockito.mock(AiPromptRepository::class.java)
        Mockito.`when`(repo.findFirstByChannelAndIsActiveTrueAndDeletedAtIsNull("blog")).thenReturn(null)
        val resolver = PromptResolver(repo)

        assertThat(resolver.resolve("blog")).isNull()
    }

    @Test
    fun `결과를 캐시하고 invalidate 시 재조회한다`() {
        val repo = Mockito.mock(AiPromptRepository::class.java)
        Mockito.`when`(repo.findFirstByChannelAndIsActiveTrueAndDeletedAtIsNull("blog")).thenReturn(activePrompt())
        val resolver = PromptResolver(repo)

        resolver.resolve("blog")
        resolver.resolve("blog") // 캐시 히트 — 재조회 없음
        Mockito.verify(repo, Mockito.times(1)).findFirstByChannelAndIsActiveTrueAndDeletedAtIsNull("blog")

        resolver.invalidate("blog")
        resolver.resolve("blog") // 무효화 후 재조회
        Mockito.verify(repo, Mockito.times(2)).findFirstByChannelAndIsActiveTrueAndDeletedAtIsNull("blog")
    }

    @Test
    fun `TTL 만료 시 재조회한다`() {
        val repo = Mockito.mock(AiPromptRepository::class.java)
        Mockito.`when`(repo.findFirstByChannelAndIsActiveTrueAndDeletedAtIsNull("blog")).thenReturn(activePrompt())
        var now = 0L
        val resolver = PromptResolver(repo, clock = { now })

        resolver.resolve("blog")
        now += 5 * 60 * 1000L + 1 // TTL 경과
        resolver.resolve("blog")

        Mockito.verify(repo, Mockito.times(2)).findFirstByChannelAndIsActiveTrueAndDeletedAtIsNull("blog")
    }
}
