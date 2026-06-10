package kr.ai.flori.photos.dto

import jakarta.validation.constraints.NotBlank
import kr.ai.flori.photos.entity.PhotoTag
import java.time.Instant

data class PhotoTagCreateRequest(
    @field:NotBlank(message = "태그 이름은 필수입니다")
    val name: String?,
    val color: String? = null,
)

data class PhotoTagUpdateRequest(
    @field:NotBlank(message = "태그 이름은 필수입니다")
    val name: String?,
    @field:NotBlank(message = "색상은 필수입니다")
    val color: String?,
)

data class PhotoTagResponse(
    val id: Long,
    val name: String,
    val color: String,
    val createdAt: Instant,
) {
    companion object {
        fun from(t: PhotoTag): PhotoTagResponse =
            PhotoTagResponse(
                id = requireNotNull(t.id),
                name = t.name,
                color = t.color,
                createdAt = t.createdAt,
            )
    }
}
