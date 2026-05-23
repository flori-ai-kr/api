package com.hazel.photos.controller

import com.hazel.photos.dto.PhotoCardCreateRequest
import com.hazel.photos.dto.PhotoCardResponse
import com.hazel.photos.dto.PhotoCardUpdateRequest
import com.hazel.photos.dto.PhotoCardsPageResponse
import com.hazel.photos.dto.ReorderPhotosRequest
import com.hazel.photos.dto.UploadTargetResponse
import com.hazel.photos.dto.UploadTargetsRequest
import com.hazel.photos.entity.PhotoFile
import com.hazel.photos.service.PhotoCardService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
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
import java.util.UUID

@Tag(name = "PhotoCards", description = "사진첩")
@RestController
@RequestMapping("/photo-cards")
class PhotoCardController(
    private val photoCardService: PhotoCardService,
) {
    @Operation(summary = "사진 카드 목록", description = "커서 페이지네이션 + tag/customerId 필터")
    @GetMapping
    fun list(
        @RequestParam(required = false) tag: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) customerId: String?,
    ): PhotoCardsPageResponse = photoCardService.list(tag, cursor, customerId)

    @Operation(summary = "매출별 사진 카드", description = "없으면 204")
    @GetMapping("/by-sale/{saleId}")
    fun bySale(
        @PathVariable saleId: UUID,
    ): ResponseEntity<PhotoCardResponse> =
        photoCardService.getBySaleId(saleId)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.noContent().build()

    @Operation(summary = "사진 카드 단건 조회")
    @GetMapping("/{id}")
    fun get(
        @PathVariable id: UUID,
    ): PhotoCardResponse = photoCardService.get(id)

    @Operation(summary = "사진 카드 생성")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: PhotoCardCreateRequest,
    ): PhotoCardResponse = photoCardService.create(request)

    @Operation(summary = "사진 카드 수정")
    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: PhotoCardUpdateRequest,
    ): PhotoCardResponse = photoCardService.update(id, request)

    @Operation(summary = "사진 카드 삭제", description = "정리 대상 사진 목록 반환")
    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: UUID,
    ): List<PhotoFile> = photoCardService.delete(id)

    @Operation(summary = "presigned 업로드 타깃 발급", description = "소유권/장수/이미지 메타 검증")
    @PostMapping("/{id}/upload-targets")
    fun uploadTargets(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UploadTargetsRequest,
    ): List<UploadTargetResponse> = photoCardService.createUploadTargets(id, requireNotNull(request.files))

    @Operation(summary = "사진 순서 변경")
    @PatchMapping("/{id}/photos/reorder")
    fun reorder(
        @PathVariable id: UUID,
        @Valid @RequestBody request: ReorderPhotosRequest,
    ): PhotoCardResponse = photoCardService.reorderPhotos(id, requireNotNull(request.photos))

    @Operation(summary = "사진 1장 삭제")
    @DeleteMapping("/{id}/photos")
    fun deletePhoto(
        @PathVariable id: UUID,
        @RequestParam url: String,
    ): PhotoCardResponse = photoCardService.deletePhoto(id, url)
}
