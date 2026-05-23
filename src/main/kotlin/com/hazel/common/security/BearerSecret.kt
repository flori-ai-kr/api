package com.hazel.common.security

import java.security.MessageDigest

/**
 * Bearer 시크릿(고정 키) 검증 공통 유틸. 내부 API·웹훅 등 "사전 공유 키" 인증에서 재사용.
 *
 * 비교는 길이에 무관한 타이밍-세이프 방식(SHA-256 다이제스트 비교) — 입력 길이/조기반환으로 인한 정보 노출 차단.
 */
object BearerSecret {
    private const val BEARER_PREFIX = "Bearer "

    /** `Authorization: Bearer <token>` 헤더에서 토큰만 추출. 접두사가 없으면 null. */
    fun extract(authorizationHeader: String?): String? =
        authorizationHeader
            ?.removePrefix(BEARER_PREFIX)
            ?.takeIf { it != authorizationHeader }

    /** 제공값이 시크릿과 일치하는지. 시크릿 미설정(blank)이거나 제공값이 null이면 항상 false(안전한 기본값). */
    fun matches(
        provided: String?,
        secret: String,
    ): Boolean = provided != null && secret.isNotBlank() && MessageDigest.isEqual(sha256(provided), sha256(secret))

    private fun sha256(value: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
}
