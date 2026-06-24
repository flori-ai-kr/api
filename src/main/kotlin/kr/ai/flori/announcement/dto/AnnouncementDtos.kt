package kr.ai.flori.announcement.dto

import jakarta.validation.constraints.NotBlank
import java.time.Instant

/** 공지 배너 1건 응답(운영자/점주 공용). */
data class AnnouncementResponse(
    val id: Long,
    val placement: String,
    val title: String,
    val body: String?,
    val imageUrl: String?,
    val linkUrl: String?,
    val isActive: Boolean,
    val startsAt: Instant?,
    val endsAt: Instant?,
    val clickCount: Int,
    val createdBy: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class AnnouncementCreateRequest(
    val placement: String = "modal",
    @field:NotBlank(message = "제목은 필수입니다")
    val title: String,
    val body: String? = null,
    val imageUrl: String? = null,
    val linkUrl: String? = null,
    val isActive: Boolean = false,
    val startsAt: Instant? = null,
    val endsAt: Instant? = null,
)

data class AnnouncementUpdateRequest(
    val placement: String? = null,
    val title: String? = null,
    val body: String? = null,
    val imageUrl: String? = null,
    val linkUrl: String? = null,
    val isActive: Boolean? = null,
    val startsAt: Instant? = null,
    val endsAt: Instant? = null,
)

data class AnnouncementSetActiveRequest(
    val active: Boolean? = null,
)
