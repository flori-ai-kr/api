package kr.ai.flori.admin.service

import kr.ai.flori.admin.dto.AdminVerificationResponse
import kr.ai.flori.admin.error.AdminErrorCode
import kr.ai.flori.admin.event.BusinessVerificationReviewedEvent
import kr.ai.flori.common.error.AppException
import kr.ai.flori.verification.domain.BusinessVerificationStatuses
import kr.ai.flori.verification.entity.BusinessVerification
import kr.ai.flori.verification.repository.BusinessVerificationRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 운영 콘솔 사업자 인증 심사. cross-tenant — @RequiresAdmin 하위에서만 호출된다.
 * 승인/거절은 엔티티 도메인 메서드(approve/reject)로 전이하고 리뷰 이벤트를 발행한다.
 */
@Service
class AdminVerificationService(
    private val repository: BusinessVerificationRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional(readOnly = true)
    fun list(
        status: BusinessVerificationStatuses,
        page: Int,
        size: Int,
    ): List<AdminVerificationResponse> =
        repository
            .findByStatusOrderByCreatedAtDesc(
                status,
                PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE)),
            ).content
            .map { it.toResponse() }

    @Transactional
    fun approve(id: Long): AdminVerificationResponse {
        val verification = load(id)
        if (verification.status != BusinessVerificationStatuses.PENDING) {
            throw AppException(AdminErrorCode.INVALID_VERIFICATION_STATE)
        }
        verification.approve()
        repository.save(verification)
        eventPublisher.publishEvent(
            BusinessVerificationReviewedEvent(verification.userId, verification.businessName, true, null),
        )
        return verification.toResponse()
    }

    @Transactional
    fun reject(
        id: Long,
        reason: String,
    ): AdminVerificationResponse {
        val verification = load(id)
        if (verification.status != BusinessVerificationStatuses.PENDING) {
            throw AppException(AdminErrorCode.INVALID_VERIFICATION_STATE)
        }
        verification.reject(reason)
        repository.save(verification)
        eventPublisher.publishEvent(
            BusinessVerificationReviewedEvent(verification.userId, verification.businessName, false, reason),
        )
        return verification.toResponse()
    }

    private fun load(id: Long): BusinessVerification =
        repository.findById(id).orElseThrow { AppException(AdminErrorCode.VERIFICATION_NOT_FOUND) }

    private fun BusinessVerification.toResponse() =
        AdminVerificationResponse(
            id = id!!,
            userId = userId,
            businessNumber = businessNumber,
            businessName = businessName,
            representativeName = representativeName,
            businessLicenseUrl = businessLicenseUrl,
            status = status.name,
            rejectReason = rejectReason,
            submittedAt = createdAt,
            reviewedAt = reviewedAt,
        )

    private companion object {
        const val MAX_PAGE_SIZE = 200
    }
}
