package com.hazel.photos.dto

import com.hazel.photos.entity.PhotoCard
import com.hazel.photos.entity.PhotoFile
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class PhotoCardCreateRequest(
    @field:NotBlank(message = "제목은 필수입니다")
    val title: String?,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val photos: List<PhotoFile> = emptyList(),
    val saleId: UUID? = null,
)

data class PhotoCardUpdateRequest(
    val title: String? = null,
    val description: String? = null,
    val tags: List<String>? = null,
    val photos: List<PhotoFile>? = null,
    val saleId: UUID? = null,
)

data class ReorderPhotosRequest(
    @field:NotNull(message = "사진 목록은 필수입니다")
    val photos: List<PhotoFile>?,
)

data class FileMetaRequest(
    @field:NotBlank val name: String?,
    @field:NotBlank val type: String?,
    val size: Long = 0,
)

data class UploadTargetsRequest(
    @field:NotEmpty(message = "파일 메타가 필요합니다")
    val files: List<FileMetaRequest>?,
)

data class UploadTargetResponse(
    val uploadUrl: String,
    val fileUrl: String,
    val originalName: String,
)

data class PhotoCardResponse(
    val id: UUID,
    val title: String,
    val description: String?,
    val tags: List<String>,
    val photos: List<PhotoFile>,
    val saleId: UUID?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(c: PhotoCard): PhotoCardResponse =
            PhotoCardResponse(
                id = requireNotNull(c.id),
                title = c.title,
                description = c.description,
                tags = c.tags,
                photos = c.photos,
                saleId = c.saleId,
                createdAt = c.createdAt,
                updatedAt = c.updatedAt,
            )
    }
}

data class PhotoCardsPageResponse(
    val cards: List<PhotoCardResponse>,
    val nextCursor: Instant?,
    val hasMore: Boolean,
)
