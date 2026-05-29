package kr.ai.flori.verification.service

import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.storage.S3PresignService
import kr.ai.flori.common.storage.StorageProperties
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.verification.domain.BusinessVerificationStatuses
import kr.ai.flori.verification.dto.BusinessLicenseUploadTargetResponse
import kr.ai.flori.verification.dto.BusinessVerificationResponse
import kr.ai.flori.verification.dto.BusinessVerificationSubmitRequest
import kr.ai.flori.verification.entity.BusinessVerification
import kr.ai.flori.verification.error.VerificationErrorCode
import kr.ai.flori.verification.event.BusinessVerificationSubmittedEvent
import kr.ai.flori.verification.repository.BusinessVerificationRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.util.UUID

/**
 * 사업자 인증 서비스. 멀티테넌시: 모든 작업은 TenantContext.currentUserId로 격리.
 * 인증됨 = APPROVED 행 존재. 승인/거절은 현재 수동(운영자 SQL).
 */
@Service
class BusinessVerificationService(
    private val repository: BusinessVerificationRepository,
    private val s3PresignService: S3PresignService,
    private val storageProperties: StorageProperties,
    private val eventPublisher: ApplicationEventPublisher,
) {
    /** 등록증 업로드 presigned PUT URL 발급. 키에 userId를 박아 소유권을 표현. */
    fun createUploadTarget(contentType: String): BusinessLicenseUploadTargetResponse {
        val userId = TenantContext.currentUserId()
        val ext =
            EXTENSION_BY_TYPE[contentType.lowercase()]
                ?: throw AppException(VerificationErrorCode.INVALID_LICENSE_TYPE)
        val key = "business-licenses/$userId/${UUID.randomUUID()}.$ext"
        val presigned = s3PresignService.presignUpload(key, contentType)
        return BusinessLicenseUploadTargetResponse(
            uploadUrl = presigned.uploadUrl,
            fileUrl = presigned.fileUrl,
            expiresInSeconds = presigned.expiresInSeconds,
        )
    }

    /** 신청 제출. 기존 PENDING/APPROVED 존재 시 409. 커밋 후 알림 이벤트 발행. */
    @Transactional
    fun submit(request: BusinessVerificationSubmitRequest): BusinessVerificationResponse {
        val userId = TenantContext.currentUserId()
        requireLicenseOwnership(userId, request.businessLicenseUrl)

        if (repository.existsByUserIdAndStatus(userId, BusinessVerificationStatuses.PENDING) ||
            repository.existsByUserIdAndStatus(userId, BusinessVerificationStatuses.APPROVED)
        ) {
            throw AppException(VerificationErrorCode.ALREADY_REQUESTED)
        }

        val saved =
            try {
                repository.save(
                    BusinessVerification(
                        userId = userId,
                        businessNumber = request.businessNumber,
                        businessName = request.businessName,
                        representativeName = request.representativeName,
                        businessLicenseUrl = request.businessLicenseUrl,
                    ),
                )
            } catch (e: DataIntegrityViolationException) {
                // 동시 신청 경쟁: 위 exists 검사를 통과해도 부분 유니크 인덱스(PENDING)가 거부할 수 있다.
                // 멱등하게 중복 신청(409)으로 변환한다.
                throw AppException(VerificationErrorCode.ALREADY_REQUESTED, cause = e)
            }

        eventPublisher.publishEvent(
            BusinessVerificationSubmittedEvent(
                userId = userId,
                businessName = saved.businessName,
                businessNumber = saved.businessNumber,
                representativeName = saved.representativeName,
                businessLicenseUrl = saved.businessLicenseUrl,
            ),
        )
        return BusinessVerificationResponse(status = saved.status.name, submittedAt = saved.createdAt)
    }

    /** 현재 사용자 최신 인증 상태(없으면 NONE). */
    @Transactional(readOnly = true)
    fun getMyStatus(): BusinessVerificationResponse {
        val latest =
            repository.findFirstByUserIdOrderByCreatedAtDesc(TenantContext.currentUserId())
                ?: return BusinessVerificationResponse.none()
        return BusinessVerificationResponse(
            status = latest.status.name,
            rejectReason = latest.rejectReason,
            submittedAt = latest.createdAt,
            reviewedAt = latest.reviewedAt,
        )
    }

    /** 게이팅용: APPROVED 행 보유 여부. */
    @Transactional(readOnly = true)
    fun isVerified(userId: Long): Boolean = repository.existsByUserIdAndStatus(userId, BusinessVerificationStatuses.APPROVED)

    /** 게이팅 가드: 현재 사용자가 미인증이면 403. */
    @Transactional(readOnly = true)
    fun requireVerified() {
        if (!isVerified(TenantContext.currentUserId())) {
            throw AppException(VerificationErrorCode.NOT_VERIFIED)
        }
    }

    /**
     * 등록증 URL이 (1) 우리 스토리지 호스트(CloudFront/S3)이고 (2) 키가 본인 prefix
     * (business-licenses/{userId}/)인지 검증. 외부 호스트 URL 주입·경로 우회를 차단한다.
     */
    private fun requireLicenseOwnership(
        userId: Long,
        fileUrl: String,
    ) {
        if (!isOwnedLicenseUrl(userId, fileUrl)) {
            throw AppException(VerificationErrorCode.LICENSE_NOT_OWNED)
        }
    }

    private fun isOwnedLicenseUrl(
        userId: Long,
        fileUrl: String,
    ): Boolean {
        val uri = runCatching { URI(fileUrl) }.getOrNull() ?: return false
        val key = uri.path?.removePrefix("/") ?: return false
        return uri.host == expectedStorageHost() &&
            key.startsWith("business-licenses/$userId/") &&
            !key.contains("..")
    }

    /** presign이 생성하는 공개 URL의 호스트(CloudFront 우선, 없으면 S3 가상호스트). */
    private fun expectedStorageHost(): String =
        storageProperties.cloudfront.domain.ifBlank {
            "${storageProperties.s3.bucket}.s3.${storageProperties.region}.amazonaws.com"
        }

    private companion object {
        val EXTENSION_BY_TYPE =
            mapOf(
                "image/jpeg" to "jpg",
                "image/png" to "png",
                "image/webp" to "webp",
                "application/pdf" to "pdf",
            )
    }
}
