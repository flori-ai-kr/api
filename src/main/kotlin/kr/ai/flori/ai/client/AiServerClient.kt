package kr.ai.flori.ai.client

import com.fasterxml.jackson.annotation.JsonProperty
import kr.ai.flori.ai.config.AiGatewayProperties
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import org.springframework.http.HttpHeaders
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

// ─── 게이트웨이 → ai-server(FastAPI, 내부망) 계약 ───────────────────
// 인증: X-Internal-Key(게이트웨이 신뢰) + Authorization Bearer(유저 JWT — ai-server가 Spring 도구 호출에 사용)
//       + X-User-Id(로깅/식별). ai-server는 stateless — 세션/영속 없음.

data class AiMessage(
    val role: String,
    val content: String,
)

data class AiChatCall(
    val messages: List<AiMessage>,
    val model: String,
)

data class AiChatResult(
    val reply: String = "",
    val model: String? = null,
    @get:JsonProperty("input_tokens") @param:JsonProperty("input_tokens") val inputTokens: Int? = null,
    @get:JsonProperty("output_tokens") @param:JsonProperty("output_tokens") val outputTokens: Int? = null,
)

data class AiServerSuggestion(
    val title: String = "",
    val detail: String = "",
)

data class AiProactiveResult(
    val suggestions: List<AiServerSuggestion> = emptyList(),
    val model: String? = null,
    @get:JsonProperty("input_tokens") @param:JsonProperty("input_tokens") val inputTokens: Int? = null,
    @get:JsonProperty("output_tokens") @param:JsonProperty("output_tokens") val outputTokens: Int? = null,
)

data class AiOcrCall(
    @get:JsonProperty("image_url") @param:JsonProperty("image_url") val imageUrl: String,
    val model: String,
)

data class AiDraft(
    @get:JsonProperty("customer_name") @param:JsonProperty("customer_name") val customerName: String? = null,
    @get:JsonProperty("customer_phone") @param:JsonProperty("customer_phone") val customerPhone: String? = null,
    val date: String? = null,
    val time: String? = null,
    val title: String? = null,
    val amount: Int? = null,
)

data class AiOcrResult(
    val draft: AiDraft = AiDraft(),
    val model: String? = null,
    @get:JsonProperty("input_tokens") @param:JsonProperty("input_tokens") val inputTokens: Int? = null,
    @get:JsonProperty("output_tokens") @param:JsonProperty("output_tokens") val outputTokens: Int? = null,
)

@Component
class AiServerClient(
    private val properties: AiGatewayProperties,
) {
    private val restClient: RestClient =
        RestClient
            .builder()
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(properties.connectTimeoutMs)
                    setReadTimeout(properties.readTimeoutMs)
                },
            ).build()

    fun chat(
        userJwt: String,
        userId: Long,
        messages: List<AiMessage>,
    ): AiChatResult = post("/chat", userJwt, userId, AiChatCall(messages, properties.chatModel), AiChatResult::class.java)

    @Suppress("TooGenericExceptionCaught") // ai-server 호출은 다양한 예외 발생 가능 — 일괄 wrap
    fun proactive(
        userJwt: String,
        userId: Long,
    ): AiProactiveResult =
        try {
            restClient
                .get()
                .uri(properties.baseUrl + "/agent/proactive")
                .headers { applyAuth(it, userJwt, userId) }
                .retrieve()
                .body(AiProactiveResult::class.java) ?: AiProactiveResult()
        } catch (e: Exception) {
            throw wrap(e)
        }

    fun ocrExtract(
        userJwt: String,
        userId: Long,
        imageUrl: String,
    ): AiOcrResult = post("/ocr/reservation", userJwt, userId, AiOcrCall(imageUrl, properties.chatModel), AiOcrResult::class.java)

    @Suppress("TooGenericExceptionCaught") // ai-server 호출은 다양한 예외 발생 가능 — 일괄 wrap
    private fun <T> post(
        path: String,
        userJwt: String,
        userId: Long,
        body: Any,
        responseType: Class<T>,
    ): T =
        try {
            restClient
                .post()
                .uri(properties.baseUrl + path)
                .headers { applyAuth(it, userJwt, userId) }
                .body(body)
                .retrieve()
                .body(responseType) ?: throw AppException(CommonErrorCode.INTERNAL, AI_ERROR_MESSAGE)
        } catch (e: AppException) {
            throw e
        } catch (e: Exception) {
            throw wrap(e)
        }

    private fun applyAuth(
        headers: HttpHeaders,
        userJwt: String,
        userId: Long,
    ) {
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer $userJwt")
        headers.set("X-Internal-Key", properties.internalKey)
        headers.set("X-User-Id", userId.toString())
    }

    /** ai-server 호출 실패를 내부 디테일 없이 사용자용 메시지로 변환한다. */
    private fun wrap(e: Exception): AppException = AppException(CommonErrorCode.INTERNAL, AI_ERROR_MESSAGE, e)

    private companion object {
        const val AI_ERROR_MESSAGE = "AI 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
    }
}
