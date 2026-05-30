package kr.ai.flori.admin.service

import kr.ai.flori.admin.config.AiHealthProperties
import kr.ai.flori.admin.dto.AiHealthResponse
import kr.ai.flori.admin.dto.AiHealthTarget
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

/**
 * ai-server/litellm 헬스 프록시. 상태/지연만 노출(키·내부 host 비노출).
 * 타깃 미설정(빈 URL) 시 건너뛰고, 도달 불가 시 DOWN으로 degrade(짧은 타임아웃).
 */
@Service
class AiHealthService(
    private val properties: AiHealthProperties,
) {
    private val restClient: RestClient =
        RestClient
            .builder()
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(CONNECT_TIMEOUT_MS)
                    setReadTimeout(READ_TIMEOUT_MS)
                },
            ).build()

    fun check(): AiHealthResponse {
        val targets =
            buildList {
                if (properties.serverUrl.isNotBlank()) add(ping("ai-server", properties.serverUrl))
                if (properties.litellmUrl.isNotBlank()) add(ping("litellm", properties.litellmUrl))
            }
        return AiHealthResponse(targets)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun ping(
        name: String,
        url: String,
    ): AiHealthTarget {
        val start = System.nanoTime()
        return try {
            restClient
                .get()
                .uri(url)
                .retrieve()
                .toBodilessEntity()
            AiHealthTarget(name, "UP", elapsedMs(start), null)
        } catch (e: Exception) {
            // 내부 host/포트/DNS 등이 새지 않도록 예외 원문 대신 안전한 분류 사유만 노출한다.
            AiHealthTarget(name, "DOWN", elapsedMs(start), safeDetail(e))
        }
    }

    /** 예외를 내부정보 없는 안전한 사유 문자열로 분류한다(원문 메시지 비노출). */
    private fun safeDetail(e: Throwable): String =
        when (e) {
            is java.net.ConnectException -> "연결 거부"
            is java.net.SocketTimeoutException -> "응답 시간 초과"
            is java.net.UnknownHostException -> "호스트 확인 실패"
            is org.springframework.web.client.HttpStatusCodeException -> "HTTP ${e.statusCode.value()}"
            else -> "도달 불가"
        }

    private fun elapsedMs(start: Long): Long = (System.nanoTime() - start) / 1_000_000

    private companion object {
        const val CONNECT_TIMEOUT_MS = 2000
        const val READ_TIMEOUT_MS = 3000
    }
}
