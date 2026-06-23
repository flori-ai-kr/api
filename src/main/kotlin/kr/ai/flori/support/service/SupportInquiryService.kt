package kr.ai.flori.support.service

import kr.ai.flori.admin.error.AdminErrorCode
import kr.ai.flori.admin.service.AdminAuditService
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.common.util.Paging
import kr.ai.flori.support.dto.InquiryAnswerRequest
import kr.ai.flori.support.dto.InquiryCreateRequest
import kr.ai.flori.support.dto.InquiryResponse
import kr.ai.flori.support.dto.toResponse
import kr.ai.flori.support.dto.validateInquiryCategory
import kr.ai.flori.support.dto.validateInquiryStatus
import kr.ai.flori.support.entity.SupportInquiry
import kr.ai.flori.support.repository.SupportInquiryRepository
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
        return repository.save(inquiry).toResponse()
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

    // ── 운영자 ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun listForAdmin(
        status: String?,
        page: Int,
        size: Int,
    ): List<InquiryResponse> =
        repository
            .search(
                status?.takeIf { it.isNotBlank() }?.let { validateInquiryStatus(it) },
                Paging.pageSize(page, size, MAX_PAGE_SIZE),
            ).content
            .map { it.toResponse() }

    @Transactional(readOnly = true)
    fun get(id: Long): InquiryResponse = load(id).toResponse()

    @Transactional
    fun answer(
        id: Long,
        request: InquiryAnswerRequest,
    ): InquiryResponse {
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
        return inquiry.toResponse()
    }

    @Transactional
    fun changeStatus(
        id: Long,
        status: String,
    ): InquiryResponse {
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
        return inquiry.toResponse()
    }

    private fun load(id: Long): SupportInquiry = repository.findById(id).orElseThrow { AppException(AdminErrorCode.INQUIRY_NOT_FOUND) }

    private companion object {
        const val MAX_PAGE_SIZE = 200
    }
}
