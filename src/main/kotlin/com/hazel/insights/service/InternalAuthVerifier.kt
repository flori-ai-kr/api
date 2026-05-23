package com.hazel.insights.service

import com.hazel.common.error.AppException
import com.hazel.common.error.ErrorCode
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.MessageDigest

/**
 * 내부 API(수집/브로드캐스트) Bearer 인증. 타이밍-세이프 비교.
 * 키 미설정 시 모든 내부 호출을 차단(안전한 기본값).
 */
@Component
class InternalAuthVerifier(
    @Value("\${internal.api-key:}") private val apiKey: String,
) {
    fun verify(authorizationHeader: String?) {
        val provided = authorizationHeader?.removePrefix(BEARER_PREFIX)?.takeIf { it != authorizationHeader }
        if (apiKey.isBlank() || provided == null || !constantTimeEquals(provided, apiKey)) {
            throw AppException(ErrorCode.UNAUTHORIZED, "내부 API 인증 실패")
        }
    }

    private fun constantTimeEquals(
        a: String,
        b: String,
    ): Boolean = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

    private companion object {
        const val BEARER_PREFIX = "Bearer "
    }
}
