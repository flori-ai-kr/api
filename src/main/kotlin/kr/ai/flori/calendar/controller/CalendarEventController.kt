package kr.ai.flori.calendar.controller

import jakarta.validation.Valid
import kr.ai.flori.calendar.dto.CalendarEventCreateRequest
import kr.ai.flori.calendar.dto.CalendarEventResponse
import kr.ai.flori.calendar.dto.CalendarEventUpdateRequest
import kr.ai.flori.calendar.service.CalendarEventService
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
@RequestMapping("/calendar-events")
class CalendarEventController(
    private val service: CalendarEventService,
) {
    @GetMapping
    fun list(
        @RequestParam month: String,
    ): List<CalendarEventResponse> = service.listByMonth(month)

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: Long,
    ): CalendarEventResponse = service.get(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: CalendarEventCreateRequest,
    ): CalendarEventResponse = service.create(request)

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody request: CalendarEventUpdateRequest,
    ): CalendarEventResponse = service.update(id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: Long,
    ) {
        service.delete(id)
    }
}
