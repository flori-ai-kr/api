package com.hazel.calendar.controller

import com.hazel.calendar.dto.CalendarEventCreateRequest
import com.hazel.calendar.dto.CalendarEventResponse
import com.hazel.calendar.dto.CalendarEventUpdateRequest
import com.hazel.calendar.service.CalendarEventService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
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
import java.util.UUID

@Tag(name = "CalendarEvents", description = "캘린더 이벤트")
@RestController
@RequestMapping("/calendar-events")
class CalendarEventController(
    private val service: CalendarEventService,
) {
    @Operation(summary = "월별 이벤트 목록", description = "월 범위와 겹치는 이벤트")
    @GetMapping
    fun list(
        @RequestParam month: String,
    ): List<CalendarEventResponse> = service.listByMonth(month)

    @Operation(summary = "이벤트 단건 조회")
    @GetMapping("/{id}")
    fun get(
        @PathVariable id: UUID,
    ): CalendarEventResponse = service.get(id)

    @Operation(summary = "이벤트 생성")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: CalendarEventCreateRequest,
    ): CalendarEventResponse = service.create(request)

    @Operation(summary = "이벤트 수정")
    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CalendarEventUpdateRequest,
    ): CalendarEventResponse = service.update(id, request)

    @Operation(summary = "이벤트 삭제")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: UUID,
    ) {
        service.delete(id)
    }
}
