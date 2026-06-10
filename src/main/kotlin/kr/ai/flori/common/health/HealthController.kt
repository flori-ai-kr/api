package kr.ai.flori.common.health

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

/**
 * DB·인증에 의존하지 않는 경량 헬스체크. 로드밸런서/앱 부팅 점검용.
 */
@RestController
class HealthController {
    @GetMapping("/health")
    fun health(): HealthResponse =
        HealthResponse(
            status = "UP",
            service = "flori-ai-api",
            time = OffsetDateTime.now(),
        )
}
