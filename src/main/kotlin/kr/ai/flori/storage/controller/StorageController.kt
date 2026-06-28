package kr.ai.flori.storage.controller

import jakarta.validation.Valid
import kr.ai.flori.storage.dto.StorageIncreaseRequestCreate
import kr.ai.flori.storage.dto.StorageRequestResponse
import kr.ai.flori.storage.dto.StorageUsageResponse
import kr.ai.flori.storage.service.StorageRequestService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/storage")
class StorageController(
    private val service: StorageRequestService,
) {
    @GetMapping("/usage")
    fun usage(): StorageUsageResponse = service.usage()

    @GetMapping("/increase-request/latest")
    fun latestRequest(): StorageRequestResponse? = service.latestRequest()

    @PostMapping("/increase-request")
    @ResponseStatus(HttpStatus.CREATED)
    fun requestIncrease(
        @Valid @RequestBody request: StorageIncreaseRequestCreate,
    ): StorageRequestResponse = service.requestIncrease(request)
}
