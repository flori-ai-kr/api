package kr.ai.flori.admin.controller

import kr.ai.flori.admin.dto.AiHealthResponse
import kr.ai.flori.admin.gating.RequiresAdmin
import kr.ai.flori.admin.service.AiHealthService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/health")
@RequiresAdmin
class AdminHealthController(
    private val service: AiHealthService,
) {
    @GetMapping("/ai")
    fun ai(): AiHealthResponse = service.check()
}
