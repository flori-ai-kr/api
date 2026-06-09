package kr.ai.flori.photos.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import kr.ai.flori.photos.entity.PhotoCard
import kr.ai.flori.photos.entity.PhotoFile
import java.time.Instant

data class PhotoCardCreateRequest(
    @field:NotBlank(message = "제목은 필수입니다")
    val title: String?,
    val memo: String? = null,
    val tags: List<String> = emptyList(),
    val photos: List<PhotoFile> = emptyList(),
    val saleId: Long? = null,
    val customerId: Long? = null,
)

data class PhotoCardUpdateRequest(
    val title: String? = null,
    val memo: String? = null,
    val tags: List<String>? = null,
    val photos: List<PhotoFile>? = null,
    val saleId: Long? = null,
    val customerId: Long? = null,
    // 명시적 해제 플래그(saleId 와 동일하게 null=미변경 이므로, 연결 해제는 이 플래그로 구분).
    val clearCustomer: Boolean = false,
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

/** 원본 다운로드용 presigned GET URL. */
data class PhotoDownloadResponse(
    val downloadUrl: String,
)

data class PhotoCardResponse(
    val id: Long,
    val title: String,
    val memo: String?,
    val tags: List<String>,
    val photos: List<PhotoFile>,
    val saleId: Long?,
    val customerId: Long?,
    val customerName: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(
            c: PhotoCard,
            customerName: String? = null,
        ): PhotoCardResponse =
            PhotoCardResponse(
                id = requireNotNull(c.id),
                title = c.title,
                memo = c.memo,
                tags = c.tags.toList(),
                photos = c.photos,
                saleId = c.saleId,
                customerId = c.customerId,
                customerName = customerName,
                createdAt = c.createdAt,
                updatedAt = c.updatedAt,
            )
    }
}

data class PhotoCardsPageResponse(
    val cards: List<PhotoCardResponse>,
    val nextCursor: String?,
    val hasMore: Boolean,
)
