package com.hazel.common.security

import com.hazel.common.error.AppException
import com.hazel.common.error.ErrorCode
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.MessageDigest

/**
 * 내부 API(수집/브로드캐스트) Bearer 인증. 길이에 무관한 타이밍-세이프 비교(SHA-256 다이제스트).
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

    // 두 값의 SHA-256 다이제스트를 비교 → 입력 길이가 항상 같아 길이 노출(early-return)을 막는다.
    private fun constantTimeEquals(
        a: String,
        b: String,
    ): Boolean = MessageDigest.isEqual(sha256(a), sha256(b))

    private fun sha256(value: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())

    private companion object {
        const val BEARER_PREFIX = "Bearer "
    }
}
