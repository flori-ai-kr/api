package kr.ai.flori.billing.service

import kr.ai.flori.admin.dto.SubscriptionCounts
import kr.ai.flori.billing.dto.AdminSubscriptionRow
import kr.ai.flori.billing.repository.SubscriptionRepository
import kr.ai.flori.common.util.Paging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AdminSubscriptionService(
    private val subscriptionRepository: SubscriptionRepository,
) {
    @Transactional(readOnly = true)
    fun counts(): SubscriptionCounts =
        SubscriptionCounts(
            active = subscriptionRepository.countByStatus("ACTIVE"),
            trialing = subscriptionRepository.countByStatus("TRIALING"),
            inGrace = subscriptionRepository.countByStatus("IN_GRACE"),
            expired = subscriptionRepository.countByStatus("EXPIRED"),
        )

    @Transactional(readOnly = true)
    fun list(
        status: String?,
        page: Int,
        size: Int,
    ): List<AdminSubscriptionRow> {
        val pageable = Paging.pageSize(page, size, MAX_PAGE_SIZE)
        val rows =
            if (status.isNullOrBlank()) {
                subscriptionRepository.findAllByOrderByCreatedAtDesc(pageable)
            } else {
                subscriptionRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
            }
        return rows.map(AdminSubscriptionRow::from)
    }

    private companion object {
        const val MAX_PAGE_SIZE = 100
    }
}
