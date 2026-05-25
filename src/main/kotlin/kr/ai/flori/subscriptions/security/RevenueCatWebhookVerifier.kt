package kr.ai.flori.subscriptions.security

import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.ErrorCode
import kr.ai.flori.common.security.BearerSecret
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * RevenueCat 웹훅 인증. RevenueCat 대시보드에 설정한 `Authorization` 헤더(Bearer 시크릿)를
 * 타이밍-세이프하게 검증한다([BearerSecret] 재사용). 시크릿 미설정 시 모든 웹훅을 차단.
 */
@Component
class RevenueCatWebhookVerifier(
    @Value("\${revenuecat.webhook-secret:}") private val webhookSecret: String,
) {
    fun verify(authorizationHeader: String?) {
        if (!BearerSecret.matches(BearerSecret.extract(authorizationHeader), webhookSecret)) {
            throw AppException(ErrorCode.UNAUTHORIZED, "웹훅 인증 실패")
        }
    }
}
