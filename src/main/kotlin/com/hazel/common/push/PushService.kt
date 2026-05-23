package com.hazel.common.push

/**
 * 푸시 발송 추상화. 구현: FCM(운영) / 로깅(로컬·미구성).
 */
interface PushService {
    fun send(message: PushMessage): PushResult
}

data class PushMessage(
    val token: String,
    val title: String,
    val body: String,
    val data: Map<String, String> = emptyMap(),
)

/**
 * @param tokenInvalid 영구 실패(미등록/유효하지 않은 토큰) — 호출측이 구독을 비활성화해야 함.
 */
data class PushResult(
    val success: Boolean,
    val tokenInvalid: Boolean = false,
)
