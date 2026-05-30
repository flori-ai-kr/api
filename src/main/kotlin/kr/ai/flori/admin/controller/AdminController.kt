package kr.ai.flori.admin.controller

import kr.ai.flori.admin.gating.RequiresAdmin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 운영 콘솔 진입점. @RequiresAdmin 가드 확인용(웹 requireAdmin()가 호출). */
@RestController
@RequestMapping("/admin")
@RequiresAdmin
class AdminController {
    @GetMapping("/me")
    fun me(): Map<String, Boolean> = mapOf("isAdmin" to true)
}
