package kr.ai.flori.ai.service

import kr.ai.flori.ai.repository.AiPromptRepository
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

/**
 * 생성 요청에 주입할 active 프롬프트의 정적 부분(SPEC-AI-008).
 * temperature는 NUMERIC(3,2) 그대로 BigDecimal — ai-server에는 JSON 숫자로 직렬화된다.
 */
data class ResolvedPrompt(
    val systemMd: String,
    val rulesMd: String,
    val outputSpecMd: String,
    val model: String?,
    val temperature: BigDecimal?,
)

/**
 * 채널별 active 프롬프트를 로드해 메모리 캐시(5분)한다. SPEC-AI-008.
 *
 * - [resolve]가 null을 반환하면 active 프롬프트가 없다는 뜻 → 호출부는 ai-server geo_rules.py 폴백.
 * - active 부재(null)도 캐시한다 — DB가 비어도 매 요청 DB를 두드리지 않게(폴백 불변식 + 비용).
 * - 콘솔의 활성화/수정/삭제 후 [invalidate]로 즉시 무효화한다.
 */
@Component
class PromptResolver(
    private val promptRepository: AiPromptRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private data class CacheEntry(
        val value: ResolvedPrompt?,
        val expiresAt: Long,
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    fun resolve(channel: String): ResolvedPrompt? {
        val now = clock()
        cache[channel]?.let { if (it.expiresAt > now) return it.value }

        val loaded =
            promptRepository.findFirstByChannelAndIsActiveTrueAndDeletedAtIsNull(channel)?.let {
                ResolvedPrompt(
                    systemMd = it.systemMd,
                    rulesMd = it.rulesMd,
                    outputSpecMd = it.outputSpecMd,
                    model = it.model,
                    temperature = it.temperature,
                )
            }
        cache[channel] = CacheEntry(loaded, now + TTL_MILLIS)
        return loaded
    }

    fun invalidate(channel: String) {
        cache.remove(channel)
    }

    private companion object {
        const val TTL_MILLIS = 5 * 60 * 1000L
    }
}
