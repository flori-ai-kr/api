package kr.ai.flori.admin.controller

import jakarta.validation.Valid
import kr.ai.flori.admin.dto.AdminUserPage
import kr.ai.flori.admin.dto.AdminUserRow
import kr.ai.flori.admin.dto.SetActiveRequest
import kr.ai.flori.admin.gating.RequiresAdmin
import kr.ai.flori.admin.service.AdminUserService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/users")
@RequiresAdmin
class AdminUserController(
    private val service: AdminUserService,
) {
    @GetMapping
    fun list(
        @RequestParam(required = false) query: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): AdminUserPage = service.list(query, page, size)

    @PostMapping("/{id}/active")
    fun setActive(
        @PathVariable id: Long,
        @Valid @RequestBody request: SetActiveRequest,
    ): AdminUserRow = service.setActive(id, request.active!!)
}
