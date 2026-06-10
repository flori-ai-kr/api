package kr.ai.flori.waitlist.ratelimit

import com.github.benmanes.caffeine.cache.Caffeine
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.request.ClientContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/**
 * 사전등록(POST /waitlist) IP 기반 rate limit.
 *
 * 공개 엔드포인트라 봇/도배로 선착순 슬롯이 소진되는 걸 막는 방어선이다.
 * 윈도(기본 10분) 내 IP당 허용 횟수(기본 5)를 넘으면 429.
 * GET /count 는 정상 폴링이라 제한하지 않는다(인터셉터를 POST에만 적용 + 메서드 가드).
 *
 * Caffeine 인메모리(단일 인스턴스 기준) — JWT dedup과 동일한 경량 패턴.
 * 다중 인스턴스로 확장 시 분산 카운터(예: Redis)로 교체 필요.
 */
@Component
class WaitlistRateLimitInterceptor(
    @Value("\${waitlist.rate-limit.max-per-window:5}") private val maxPerWindow: Int,
    @Value("\${waitlist.rate-limit.window-seconds:600}") windowSeconds: Long,
) : HandlerInterceptor {
    private val counters =
        Caffeine
            .newBuilder()
            .expireAfterWrite(Duration.ofSeconds(windowSeconds))
            .maximumSize(MAX_TRACKED_IPS)
            .build<String, AtomicInteger>()

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        // POST(등록)만 제한. GET /count 등은 통과.
        if (!request.method.equals("POST", ignoreCase = true)) return true

        val ip = ClientContext.current()?.ip ?: request.remoteAddr ?: "unknown"
        val count = counters.get(ip) { AtomicInteger(0) }.incrementAndGet()
        if (count > maxPerWindow) {
            throw AppException(CommonErrorCode.TOO_MANY_REQUESTS)
        }
        return true
    }

    private companion object {
        const val MAX_TRACKED_IPS = 10_000L
    }
}
