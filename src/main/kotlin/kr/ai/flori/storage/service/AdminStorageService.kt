package kr.ai.flori.storage.service

import kr.ai.flori.admin.service.AdminAuditService
import kr.ai.flori.common.util.Paging
import kr.ai.flori.storage.dto.AdminStorageRequestResponse
import kr.ai.flori.storage.dto.StorageUsageResponse
import kr.ai.flori.storage.entity.StorageIncreaseRequest
import kr.ai.flori.storage.repository.StorageIncreaseRequestRepository
import kr.ai.flori.user.repository.UserProfileRepository
import kr.ai.flori.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 운영 콘솔: 증설 요청 목록(cross-tenant) + 유저 quota 상향. @RequiresAdmin 하위에서만 호출. */
@Service
class AdminStorageService(
    private val requestRepository: StorageIncreaseRequestRepository,
    private val quotaService: StorageQuotaService,
    private val userRepository: UserRepository,
    private val userProfileRepository: UserProfileRepository,
    private val audit: AdminAuditService,
) {
    @Transactional(readOnly = true)
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
    fun updateQuota(
        userId: Long,
        quotaBytes: Long,
    ): StorageUsageResponse {
        quotaService.setQuota(userId, quotaBytes)
        // 해당 유저의 PENDING 요청 모두 해결 처리
        requestRepository.findByUserIdAndStatus(userId, StorageIncreaseRequest.STATUS_PENDING).forEach {
            it.resolve(quotaBytes)
            requestRepository.save(it)
        }
        audit.record(
            action = "STORAGE_QUOTA_UPDATE",
            targetType = "user_storage",
            targetId = userId.toString(),
            summary = "스토리지 한도 상향: $quotaBytes",
            metadata = mapOf("userId" to userId, "quotaBytes" to quotaBytes),
        )
        return StorageUsageResponse.from(quotaService.usage(userId))
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
            usedBytes = usage.usedBytes,
            quotaBytes = usage.quotaBytes,
            createdAt = r.createdAt,
        )
    }

    private companion object {
        const val MAX_PAGE_SIZE = 100
    }
}
