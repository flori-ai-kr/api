package kr.ai.flori.announcement.service

import kr.ai.flori.admin.error.AdminErrorCode
import kr.ai.flori.admin.service.AdminAuditService
import kr.ai.flori.announcement.dto.AnnouncementCreateRequest
import kr.ai.flori.announcement.dto.AnnouncementResponse
import kr.ai.flori.announcement.dto.AnnouncementUpdateRequest
import kr.ai.flori.announcement.entity.Announcement
import kr.ai.flori.announcement.repository.AnnouncementRepository
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.common.util.Paging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 공지 배너 CMS. 운영자 CRUD(@RequiresAdmin 하위)와 점주 노출/클릭을 함께 제공한다.
 * 운영자 변경은 감사 로그를 남기고(create/update/toggle/delete), 점주 클릭은 감사 없이 집계만 한다.
 */
@Service
class AnnouncementService(
    private val repository: AnnouncementRepository,
    private val audit: AdminAuditService,
) {
    @Transactional(readOnly = true)
    fun listForAdmin(
        page: Int,
        size: Int,
    ): List<AnnouncementResponse> =
        repository
            .findAllForAdmin(Paging.pageSize(page, size, MAX_PAGE_SIZE))
            .content
            .map { it.toResponse() }

    @Transactional
    fun create(req: AnnouncementCreateRequest): AnnouncementResponse {
        validatePlacement(req.placement)
        val announcement =
            Announcement(
                placement = req.placement,
                title = req.title,
                createdBy = TenantContext.currentUserId(),
            ).apply {
                body = req.body
                imageUrl = req.imageUrl
                linkUrl = req.linkUrl
                isActive = req.isActive
                startsAt = req.startsAt
                endsAt = req.endsAt
            }
        val saved = repository.save(announcement)
        audit.record(
            action = "ANNOUNCEMENT_CREATE",
            targetType = "announcement",
            targetId = saved.id.toString(),
            summary = "공지 \"${saved.title}\" 생성",
            metadata = mapOf("placement" to saved.placement, "isActive" to saved.isActive),
        )
        return saved.toResponse()
    }

    @Transactional
    fun update(
        id: Long,
        req: AnnouncementUpdateRequest,
    ): AnnouncementResponse {
        val announcement = load(id)
        req.placement?.let {
            validatePlacement(it)
            announcement.placement = it
        }
        req.title?.let { announcement.title = it }
        req.body?.let { announcement.body = it }
        req.imageUrl?.let { announcement.imageUrl = it }
        req.linkUrl?.let { announcement.linkUrl = it }
        req.isActive?.let { announcement.isActive = it }
        req.startsAt?.let { announcement.startsAt = it }
        req.endsAt?.let { announcement.endsAt = it }
        repository.save(announcement)
        audit.record(
            action = "ANNOUNCEMENT_UPDATE",
            targetType = "announcement",
            targetId = id.toString(),
            summary = "공지 \"${announcement.title}\" 수정",
        )
        return announcement.toResponse()
    }

    @Transactional
    fun setActive(
        id: Long,
        active: Boolean,
    ): AnnouncementResponse {
        val announcement = load(id)
        if (active) announcement.activate() else announcement.deactivate()
        repository.save(announcement)
        audit.record(
            action = "ANNOUNCEMENT_TOGGLE",
            targetType = "announcement",
            targetId = id.toString(),
            summary = "공지 \"${announcement.title}\" ${if (active) "노출" else "숨김"}",
            metadata = mapOf("isActive" to active),
        )
        return announcement.toResponse()
    }

    @Transactional
    fun delete(id: Long) {
        val announcement = load(id)
        announcement.softDelete()
        repository.save(announcement)
        audit.record(
            action = "ANNOUNCEMENT_DELETE",
            targetType = "announcement",
            targetId = id.toString(),
            summary = "공지 \"${announcement.title}\" 삭제",
        )
    }

    @Transactional(readOnly = true)
    fun listActive(placement: String?): List<AnnouncementResponse> {
        placement?.let { validatePlacement(it) }
        return repository
            .findActive(placement, Instant.now())
            .map { it.toResponse() }
    }

    @Transactional
    fun click(id: Long): AnnouncementResponse {
        val announcement = load(id)
        announcement.incrementClick()
        repository.save(announcement)
        return announcement.toResponse()
    }

    private fun load(id: Long): Announcement =
        repository.findByIdAndDeletedAtIsNull(id)
            ?: throw AppException(AdminErrorCode.ANNOUNCEMENT_NOT_FOUND)

    private fun validatePlacement(placement: String) {
        if (placement !in ALLOWED_PLACEMENTS) {
            throw AppException(CommonErrorCode.VALIDATION)
        }
    }

    private fun Announcement.toResponse() =
        AnnouncementResponse(
            id = id!!,
            placement = placement,
            title = title,
            body = body,
            imageUrl = imageUrl,
            linkUrl = linkUrl,
            isActive = isActive,
            startsAt = startsAt,
            endsAt = endsAt,
            clickCount = clickCount,
            createdBy = createdBy,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private companion object {
        const val MAX_PAGE_SIZE = 200
        val ALLOWED_PLACEMENTS = setOf("modal", "bar")
    }
}
