package kr.ai.flori.storage.service

import kr.ai.flori.admin.service.AdminAuditService
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.push.PushDispatcher
import kr.ai.flori.common.push.PushTemplates
import kr.ai.flori.common.push.PushTypes
import kr.ai.flori.common.util.Paging
import kr.ai.flori.storage.dto.AdminStorageRequestResponse
import kr.ai.flori.storage.entity.StorageIncreaseRequest
import kr.ai.flori.storage.error.StorageErrorCode
import kr.ai.flori.storage.repository.StorageIncreaseRequestRepository
import kr.ai.flori.user.repository.UserProfileRepository
import kr.ai.flori.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 운영 콘솔: 증설 요청 목록(cross-tenant) + 승인/거절. @RequiresAdmin 하위에서만 호출. */
@Service
class AdminStorageService(
    private val requestRepository: StorageIncreaseRequestRepository,
    private val quotaService: StorageQuotaService,
    private val userRepository: UserRepository,
    private val userProfileRepository: UserProfileRepository,
    private val pushDispatcher: PushDispatcher,
    private val audit: AdminAuditService,
) {
    @Transactional
    fun list(
        status: String?,
        page: Int,
        size: Int,
    ): List<AdminStorageRequestResponse> =
        requestRepository
            .search(status, Paging.pageSize(page, size, MAX_PAGE_SIZE))
            .content
            .map { toResponse(it) }

    @Transactional
    fun approve(
        requestId: Long,
        quotaBytes: Long,
    ): AdminStorageRequestResponse {
        val req = requestRepository.findById(requestId).orElseThrow { AppException(StorageErrorCode.REQUEST_NOT_FOUND) }
        if (req.status != StorageIncreaseRequest.STATUS_PENDING) {
            throw AppException(StorageErrorCode.ALREADY_PROCESSED)
        }
        req.approve(quotaBytes)
        quotaService.setQuota(req.userId, quotaBytes)
        audit.record(
            action = "STORAGE_APPROVE",
            targetType = "storage_increase_requests",
            targetId = requestId.toString(),
            summary = "증설 승인: ${quotaBytes}B",
            metadata = mapOf("userId" to req.userId, "quotaBytes" to quotaBytes),
        )
        val push = PushTemplates.storageApproved(quotaBytes)
        pushDispatcher.sendToUser(req.userId, push.title, push.body, push.link, PushTypes.STORAGE_RESOLVED)
        return toResponse(req)
    }

    @Transactional
    fun reject(
        requestId: Long,
        rejectReason: String,
    ): AdminStorageRequestResponse {
        val req = requestRepository.findById(requestId).orElseThrow { AppException(StorageErrorCode.REQUEST_NOT_FOUND) }
        if (req.status != StorageIncreaseRequest.STATUS_PENDING) {
            throw AppException(StorageErrorCode.ALREADY_PROCESSED)
        }
        req.reject(rejectReason)
        audit.record(
            action = "STORAGE_REJECT",
            targetType = "storage_increase_requests",
            targetId = requestId.toString(),
            summary = "증설 거절: $rejectReason",
            metadata = mapOf("userId" to req.userId, "reason" to rejectReason),
        )
        val push = PushTemplates.storageRejected(rejectReason)
        pushDispatcher.sendToUser(req.userId, push.title, push.body, push.link, PushTypes.STORAGE_RESOLVED)
        return toResponse(req)
    }

    private fun toResponse(r: StorageIncreaseRequest): AdminStorageRequestResponse {
        val usage = quotaService.usage(r.userId)
        return AdminStorageRequestResponse(
            id = r.id!!,
            userId = r.userId,
            nickname = userRepository.findById(r.userId).orElse(null)?.nickname,
            storeName = userProfileRepository.findById(r.userId).orElse(null)?.storeName,
            reason = r.reason,
            status = r.status,
            rejectReason = r.rejectReason,
            resolvedBytes = r.resolvedBytes,
            usedBytes = usage.usedBytes,
            quotaBytes = usage.quotaBytes,
            createdAt = r.createdAt,
        )
    }

    private companion object {
        const val MAX_PAGE_SIZE = 100
    }
}
