package kr.ai.flori.calendar.controller

import jakarta.validation.Valid
import kr.ai.flori.calendar.dto.CalendarEventCreateRequest
import kr.ai.flori.calendar.dto.CalendarEventResponse
import kr.ai.flori.calendar.dto.CalendarEventUpdateRequest
import kr.ai.flori.calendar.service.CalendarEventService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

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
