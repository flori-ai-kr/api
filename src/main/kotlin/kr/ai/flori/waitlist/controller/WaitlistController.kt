package kr.ai.flori.waitlist.controller

import jakarta.validation.Valid
import kr.ai.flori.waitlist.dto.WaitlistCountResponse
import kr.ai.flori.waitlist.dto.WaitlistRegisterRequest
import kr.ai.flori.waitlist.dto.WaitlistRegisterResponse
import kr.ai.flori.waitlist.service.WaitlistService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** 사전등록(공개). 인증 불필요 — SecurityConfig에서 permitAll. */
@RestController
@RequestMapping("/waitlist")
class WaitlistController(
    private val service: WaitlistService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun register(
        @Valid @RequestBody request: WaitlistRegisterRequest,
    ): WaitlistRegisterResponse = service.register(request)

    @GetMapping("/count")
    fun count(): WaitlistCountResponse = service.count()
}
