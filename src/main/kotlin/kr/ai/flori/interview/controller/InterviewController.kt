package kr.ai.flori.interview.controller

import jakarta.validation.Valid
import kr.ai.flori.interview.dto.InterviewApplyRequest
import kr.ai.flori.interview.dto.InterviewApplyResponse
import kr.ai.flori.interview.service.InterviewService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** 유저 인터뷰 모집(공개). 인증 불필요 — SecurityConfig에서 permitAll. */
@RestController
@RequestMapping("/interview")
class InterviewController(
    private val service: InterviewService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun apply(
        @Valid @RequestBody request: InterviewApplyRequest,
    ): InterviewApplyResponse = service.apply(request)
}
