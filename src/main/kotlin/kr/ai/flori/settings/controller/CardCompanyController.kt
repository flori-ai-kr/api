package kr.ai.flori.settings.controller

import jakarta.validation.Valid
import kr.ai.flori.settings.dto.CardCompanyCreateRequest
import kr.ai.flori.settings.dto.CardCompanyResponse
import kr.ai.flori.settings.dto.CardCompanyUpdateRequest
import kr.ai.flori.settings.dto.UpdateBottomNavRequest
import kr.ai.flori.settings.dto.UserPreferencesResponse
import kr.ai.flori.settings.service.CardCompanyService
import kr.ai.flori.settings.service.UserPreferenceService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/settings")
class CardCompanyController(
    private val cardCompanyService: CardCompanyService,
    private val userPreferenceService: UserPreferenceService,
) {
    @GetMapping("/card-companies")
    fun cards(): List<CardCompanyResponse> = cardCompanyService.list()

    @PostMapping("/card-companies")
    @ResponseStatus(HttpStatus.CREATED)
    fun createCard(
        @Valid @RequestBody request: CardCompanyCreateRequest,
    ): CardCompanyResponse = cardCompanyService.create(requireNotNull(request.name), request.feeRate, request.depositDays)

    @PatchMapping("/card-companies/{id}")
    fun updateCard(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CardCompanyUpdateRequest,
    ): CardCompanyResponse = cardCompanyService.update(id, request.feeRate, request.depositDays)

    @DeleteMapping("/card-companies/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteCard(
        @PathVariable id: UUID,
    ) {
        cardCompanyService.delete(id)
    }

    @GetMapping("/preferences")
    fun preferences(): UserPreferencesResponse = userPreferenceService.get()

    @PutMapping("/preferences/bottom-nav")
    fun updateBottomNav(
        @Valid @RequestBody request: UpdateBottomNavRequest,
    ): UserPreferencesResponse = userPreferenceService.updateBottomNav(requireNotNull(request.items))
}
