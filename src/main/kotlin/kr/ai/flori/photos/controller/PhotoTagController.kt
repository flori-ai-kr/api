package kr.ai.flori.photos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
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
import java.util.UUID

@Tag(name = "PhotoTags", description = "사진 태그")
@RestController
@RequestMapping("/photo-tags")
class PhotoTagController(
    private val photoTagService: PhotoTagService,
) {
    @Operation(summary = "태그 목록")
    @GetMapping
    fun list(): List<PhotoTagResponse> = photoTagService.list()

    @Operation(summary = "태그 생성", description = "색상 미지정 시 랜덤")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: PhotoTagCreateRequest,
    ): PhotoTagResponse = photoTagService.create(requireNotNull(request.name), request.color)

    @Operation(summary = "태그 수정")
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: PhotoTagUpdateRequest,
    ): PhotoTagResponse = photoTagService.update(id, requireNotNull(request.name), requireNotNull(request.color))

    @Operation(summary = "태그 삭제", description = "사용 중인 카드에서도 제거")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: UUID,
    ) {
        photoTagService.delete(id)
    }
}
