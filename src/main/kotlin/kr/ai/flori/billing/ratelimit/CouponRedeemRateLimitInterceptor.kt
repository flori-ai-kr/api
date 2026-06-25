package kr.ai.flori.billing.ratelimit

import com.github.benmanes.caffeine.cache.Caffeine
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.tenant.TenantContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/** 쿠폰 redeem 무차별 대입 방지. 유저(폴백 IP)당 윈도 제한. 단일 인스턴스 인메모리(Caffeine). */
@Component
class CouponRedeemRateLimitInterceptor(
    @Value("\${coupon.rate-limit.max-per-window:10}") private val maxPerWindow: Int,
    @Value("\${coupon.rate-limit.window-seconds:600}") windowSeconds: Long,
) : HandlerInterceptor {
    private val counters =
        Caffeine
            .newBuilder()
            .expireAfterWrite(Duration.ofSeconds(windowSeconds))
            .maximumSize(MAX_TRACKED)
            .build<String, AtomicInteger>()

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        if (!request.method.equals("POST", ignoreCase = true)) return true
        val key = TenantContext.currentUserIdOrNull()?.toString() ?: request.remoteAddr ?: "unknown"
        val count = counters.get(key) { AtomicInteger(0) }.incrementAndGet()
        if (count > maxPerWindow) throw AppException(CommonErrorCode.TOO_MANY_REQUESTS)
        return true
    }

    private companion object {
        const val MAX_TRACKED = 10_000L
    }
}
