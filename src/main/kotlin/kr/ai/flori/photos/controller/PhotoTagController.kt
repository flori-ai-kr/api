package kr.ai.flori.photos.controller

import jakarta.validation.Valid
import kr.ai.flori.photos.dto.PhotoTagCreateRequest
import kr.ai.flori.photos.dto.PhotoTagResponse
import kr.ai.flori.photos.dto.PhotoTagUpdateRequest
import kr.ai.flori.photos.service.PhotoTagService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/photo-tags")
class PhotoTagController(
    private val photoTagService: PhotoTagService,
) {
    @GetMapping
    fun list(): List<PhotoTagResponse> = photoTagService.list()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: PhotoTagCreateRequest,
    ): PhotoTagResponse = photoTagService.create(requireNotNull(request.name))

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody request: PhotoTagUpdateRequest,
    ): PhotoTagResponse = photoTagService.update(id, requireNotNull(request.name))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: Long,
    ) {
        photoTagService.delete(id)
    }
}
