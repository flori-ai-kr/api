package kr.ai.flori.support.service

import kr.ai.flori.admin.error.AdminErrorCode
import kr.ai.flori.admin.service.AdminAuditService
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.storage.S3PresignService
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.common.util.Paging
import kr.ai.flori.support.dto.AdminInquiryResponse
import kr.ai.flori.support.dto.InquiryAnswerRequest
import kr.ai.flori.support.dto.InquiryCreateRequest
import kr.ai.flori.support.dto.InquiryResponse
import kr.ai.flori.support.dto.InquiryUploadRequest
import kr.ai.flori.support.dto.InquiryUploadTargetResponse
import kr.ai.flori.support.dto.toAdminResponse
import kr.ai.flori.support.dto.toResponse
import kr.ai.flori.support.dto.validateInquiryCategory
import kr.ai.flori.support.dto.validateInquiryStatus
import kr.ai.flori.support.entity.SupportInquiry
import kr.ai.flori.support.event.InquiryAnsweredEvent
import kr.ai.flori.support.event.InquiryCreatedEvent
import kr.ai.flori.support.repository.SupportInquiryRepository
import kr.ai.flori.user.repository.UserProfileRepository
import kr.ai.flori.user.repository.UserRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 1:1 문의·피드백 인박스.
 * 점주(create/listMine)는 TenantContext로 본인 데이터만 다루고,
 * 운영자(listForAdmin/get/answer/changeStatus)는 cross-tenant로 답변/상태를 관리한다.
 * 운영자 액션은 AdminAuditService로 같은 트랜잭션에서 감사 기록한다.
 */
@Service
class SupportInquiryService(
    private val repository: SupportInquiryRepository,
    private val audit: AdminAuditService,
    private val eventPublisher: ApplicationEventPublisher,
    private val s3PresignService: S3PresignService,
    private val userRepository: UserRepository,
    private val userProfileRepository: UserProfileRepository,
) {
    // ── 점주 ──────────────────────────────────────────────────────────────

    @Transactional
    fun create(request: InquiryCreateRequest): InquiryResponse {
        val inquiry =
            SupportInquiry(
                userId = TenantContext.currentUserId(),
                category = validateInquiryCategory(request.category),
                title = requireNotNull(request.title),
                body = requireNotNull(request.body),
            ).apply {
                imageUrls = request.imageUrls.toTypedArray()
                status = "open"
            }
        val saved = repository.save(inquiry)
        eventPublisher.publishEvent(
            InquiryCreatedEvent(
                inquiryId = saved.id!!,
                userId = saved.userId,
                category = saved.category,
                title = saved.title,
                nickname = userRepository.findById(saved.userId).orElse(null)?.nickname,
                storeName = userProfileRepository.findById(saved.userId).orElse(null)?.storeName,
            ),
        )
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    fun listMine(
        page: Int,
        size: Int,
    ): List<InquiryResponse> =
        repository
            .findByUserIdOrderByCreatedAtDesc(
                TenantContext.currentUserId(),
                Paging.pageSize(page, size, MAX_PAGE_SIZE),
            ).content
            .map { it.toResponse() }

    fun createUploadTargets(files: List<InquiryUploadRequest.InquiryFileInfo>): List<InquiryUploadTargetResponse> {
        val userId = TenantContext.currentUserId()
        return files.map { file ->
            if (!file.type.startsWith("image/")) throw AppException(CommonErrorCode.VALIDATION, "이미지만 업로드할 수 있습니다")
            val safeName =
                file.name
                    .substringAfterLast('/')
                    .substringAfterLast('\\')
                    .replace("..", "")
                    .ifBlank { "image" }
            val key = "support/$userId/${System.currentTimeMillis()}-$safeName"
            val presigned = s3PresignService.presignUpload(key, file.type)
            InquiryUploadTargetResponse(
                uploadUrl = presigned.uploadUrl,
                publicUrl = presigned.fileUrl,
                originalName = file.name,
            )
        }
    }

    // ── 운영자 ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun listForAdmin(
        status: String?,
        page: Int,
        size: Int,
    ): List<AdminInquiryResponse> {
        val inquiries =
            repository
                .search(
                    status?.takeIf { it.isNotBlank() }?.let { validateInquiryStatus(it) },
                    Paging.pageSize(page, size, MAX_PAGE_SIZE),
                ).content
        return withAuthors(inquiries)
    }

    @Transactional(readOnly = true)
    fun get(id: Long): AdminInquiryResponse = withAuthors(listOf(load(id))).first()

    @Transactional
    fun answer(
        id: Long,
        request: InquiryAnswerRequest,
    ): AdminInquiryResponse {
        val inquiry = load(id)
        inquiry.answer(requireNotNull(request.answer), TenantContext.currentUserId())
        request.status?.takeIf { it.isNotBlank() }?.let { inquiry.changeStatus(validateInquiryStatus(it)) }
        repository.save(inquiry)
        audit.record(
            action = "INQUIRY_ANSWER",
            targetType = "inquiry",
            targetId = id.toString(),
            summary = "문의 답변: ${inquiry.title}",
            metadata = mapOf("userId" to inquiry.userId, "after" to mapOf("status" to inquiry.status)),
        )
        eventPublisher.publishEvent(
            InquiryAnsweredEvent(
                inquiryId = id,
                userId = inquiry.userId,
                title = inquiry.title,
            ),
        )
        return withAuthors(listOf(inquiry)).first()
    }

    @Transactional
    fun changeStatus(
        id: Long,
        status: String,
    ): AdminInquiryResponse {
        val inquiry = load(id)
        inquiry.changeStatus(validateInquiryStatus(status))
        repository.save(inquiry)
        audit.record(
            action = "INQUIRY_STATUS",
            targetType = "inquiry",
            targetId = id.toString(),
            summary = "문의 상태 변경: ${inquiry.title}",
            metadata = mapOf("userId" to inquiry.userId, "after" to mapOf("status" to inquiry.status)),
        )
        return withAuthors(listOf(inquiry)).first()
    }

    /**
     * 문의 목록에 작성자(닉네임·가게명)를 배치 조회로 합친다(N+1 회피).
     * users는 항상 존재, user_profiles는 온보딩 전이면 없을 수 있어 null 허용.
     */
    private fun withAuthors(inquiries: List<SupportInquiry>): List<AdminInquiryResponse> {
        val userIds = inquiries.map { it.userId }.distinct()
        val nicknames = userRepository.findAllById(userIds).associate { it.id to it.nickname }
        val storeNames = userProfileRepository.findAllById(userIds).associate { it.userId to it.storeName }
        return inquiries.map { it.toAdminResponse(nicknames[it.userId], storeNames[it.userId]) }
    }

    private fun load(id: Long): SupportInquiry = repository.findById(id).orElseThrow { AppException(AdminErrorCode.INQUIRY_NOT_FOUND) }

    private companion object {
        const val MAX_PAGE_SIZE = 200
    }
}
