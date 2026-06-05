package kr.ai.flori.schedules.controller

import jakarta.validation.Valid
import kr.ai.flori.schedules.dto.ScheduleCreateRequest
import kr.ai.flori.schedules.dto.ScheduleResponse
import kr.ai.flori.schedules.dto.ScheduleUpdateRequest
import kr.ai.flori.schedules.service.ScheduleService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/schedules")
class ScheduleController(
    private val service: ScheduleService,
) {
    @GetMapping
    fun list(
        @RequestParam month: String,
    ): List<ScheduleResponse> = service.listByMonth(month)

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: Long,
    ): ScheduleResponse = service.get(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: ScheduleCreateRequest,
    ): ScheduleResponse = service.create(request)

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody request: ScheduleUpdateRequest,
    ): ScheduleResponse = service.update(id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: Long,
    ) {
        service.delete(id)
    }
}
