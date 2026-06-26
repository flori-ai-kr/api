package kr.ai.flori.storage.service

import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.storage.dto.StorageIncreaseRequestCreate
import kr.ai.flori.storage.dto.StorageRequestResponse
import kr.ai.flori.storage.dto.StorageUsageResponse
import kr.ai.flori.storage.entity.StorageIncreaseRequest
import kr.ai.flori.storage.error.StorageErrorCode
import kr.ai.flori.storage.event.StorageIncreaseRequestedEvent
import kr.ai.flori.storage.repository.StorageIncreaseRequestRepository
import kr.ai.flori.user.repository.UserProfileRepository
import kr.ai.flori.user.repository.UserRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 점주용 스토리지 사용량 조회 + 증설 요청 접수(이벤트 발행) + 최신 요청 조회. */
@Service
class StorageRequestService(
    private val quotaService: StorageQuotaService,
    private val requestRepository: StorageIncreaseRequestRepository,
    private val userRepository: UserRepository,
    private val userProfileRepository: UserProfileRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun usage(): StorageUsageResponse = StorageUsageResponse.from(quotaService.usage(TenantContext.currentUserId()))

    @Transactional(readOnly = true)
    fun latestRequest(): StorageRequestResponse? {
        val userId = TenantContext.currentUserId()
        return requestRepository.findTopByUserIdOrderByCreatedAtDesc(userId)?.let { StorageRequestResponse.from(it) }
    }

    @Transactional
    fun requestIncrease(request: StorageIncreaseRequestCreate): StorageRequestResponse {
        val userId = TenantContext.currentUserId()
        val pending = requestRepository.findByUserIdAndStatus(userId, StorageIncreaseRequest.STATUS_PENDING)
        if (pending.isNotEmpty()) throw AppException(StorageErrorCode.DUPLICATE_PENDING)

        val saved = requestRepository.save(StorageIncreaseRequest(userId = userId, reason = request.reason))
        val usage = quotaService.usage(userId)
        eventPublisher.publishEvent(
            StorageIncreaseRequestedEvent(
                requestId = saved.id!!,
                userId = userId,
                reason = saved.reason,
                nickname = userRepository.findById(userId).orElse(null)?.nickname,
                storeName = userProfileRepository.findById(userId).orElse(null)?.storeName,
                usedBytes = usage.usedBytes,
                quotaBytes = usage.quotaBytes,
            ),
        )
        return StorageRequestResponse.from(saved)
    }
}
