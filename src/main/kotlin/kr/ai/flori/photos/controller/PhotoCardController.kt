package kr.ai.flori.photos.controller

import jakarta.validation.Valid
import kr.ai.flori.photos.dto.PhotoCardCreateRequest
import kr.ai.flori.photos.dto.PhotoCardResponse
import kr.ai.flori.photos.dto.PhotoCardUpdateRequest
import kr.ai.flori.photos.dto.PhotoCardsPageResponse
import kr.ai.flori.photos.dto.PhotoDownloadResponse
import kr.ai.flori.photos.dto.ReorderPhotosRequest
import kr.ai.flori.photos.dto.UploadTargetResponse
import kr.ai.flori.photos.dto.UploadTargetsRequest
import kr.ai.flori.photos.service.PhotoCardService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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
@RequestMapping("/photo-cards")
class PhotoCardController(
    private val photoCardService: PhotoCardService,
) {
    @GetMapping
    fun list(
        @RequestParam(required = false) tag: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) customerId: String?,
    ): PhotoCardsPageResponse = photoCardService.list(tag, cursor, customerId)

    @GetMapping("/by-sale/{saleId}")
    fun bySale(
        @PathVariable saleId: Long,
    ): ResponseEntity<PhotoCardResponse> =
        photoCardService.getBySaleId(saleId)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.noContent().build()

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: Long,
    ): PhotoCardResponse = photoCardService.get(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: PhotoCardCreateRequest,
    ): PhotoCardResponse = photoCardService.create(request)

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody request: PhotoCardUpdateRequest,
    ): PhotoCardResponse = photoCardService.update(id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: Long,
    ) {
        photoCardService.delete(id)
    }

    @PostMapping("/upload-targets")
    fun prepareUploadTargets(
        @Valid @RequestBody request: UploadTargetsRequest,
    ): List<UploadTargetResponse> = photoCardService.createUploadTargets(requireNotNull(request.files))

    @PostMapping("/{id}/upload-targets")
    fun uploadTargets(
        @PathVariable id: Long,
        @Valid @RequestBody request: UploadTargetsRequest,
    ): List<UploadTargetResponse> = photoCardService.createUploadTargets(id, requireNotNull(request.files))

    @PatchMapping("/{id}/photos/reorder")
    fun reorder(
        @PathVariable id: Long,
        @Valid @RequestBody request: ReorderPhotosRequest,
    ): PhotoCardResponse = photoCardService.reorderPhotos(id, requireNotNull(request.photos))

    @DeleteMapping("/{id}/photos")
    fun deletePhoto(
        @PathVariable id: Long,
        @RequestParam url: String,
    ): PhotoCardResponse = photoCardService.deletePhoto(id, url)

    @GetMapping("/{id}/photos/download")
    fun download(
        @PathVariable id: Long,
        @RequestParam url: String,
    ): PhotoDownloadResponse = PhotoDownloadResponse(photoCardService.downloadUrl(id, url))
}
