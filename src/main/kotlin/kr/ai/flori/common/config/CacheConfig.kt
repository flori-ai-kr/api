package kr.ai.flori.common.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

/**
 * 캐시 설정. 변경이 드물고 요청마다 반복 조회되는 참조성 데이터(라벨 설정)를 Caffeine 인메모리로 캐싱한다.
 *
 * - 쓰기 시 [kr.ai.flori.settings.service.LabelSettingService] 가 @CacheEvict 로 무효화하므로 정합성은 보장된다.
 * - expireAfterWrite(10분)는 혹시 모를 evict 누락에 대한 안전망(staleness 상한).
 * - 키에 테넌트(userId)를 포함하므로 테넌트 간 라벨이 섞이지 않는다(@Cacheable key SpEL 참조).
 */
@Configuration
@EnableCaching
class CacheConfig {
    @Bean
    fun cacheManager(): CaffeineCacheManager {
        val caffeine =
            Caffeine
                .newBuilder()
                .expireAfterWrite(TTL_MINUTES, TimeUnit.MINUTES)
                .maximumSize(MAX_ENTRIES)
        // 명시한 캐시 이름만 허용(오타로 인한 동적 캐시 생성 방지).
        return CaffeineCacheManager(LABEL_MAP_CACHE).apply { setCaffeine(caffeine) }
    }

    companion object {
        /** 라벨 설정(label_settings) id→label 맵 캐시. 키: "{userId}:{domain}:{kind}". */
        const val LABEL_MAP_CACHE = "labelMap"

        /** evict 누락 안전망: 쓰기 후 10분이면 자동 만료. */
        private const val TTL_MINUTES = 10L

        /** 테넌트×(domain,kind) 조합 상한(메모리 가드). */
        private const val MAX_ENTRIES = 10_000L
    }
}
