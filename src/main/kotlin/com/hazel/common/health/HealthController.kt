package com.hazel.common.health

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

/**
 * DB·인증에 의존하지 않는 경량 헬스체크. 로드밸런서/앱 부팅 점검용.
 */
@Tag(name = "Health", description = "서버 상태 점검")
@RestController
class HealthController {
    @Operation(summary = "헬스체크", description = "서버 가용 여부를 반환한다.")
    @GetMapping("/health")
    fun health(): HealthResponse =
        HealthResponse(
            status = "UP",
            service = "hazel-server",
            time = OffsetDateTime.now(),
        )
}
