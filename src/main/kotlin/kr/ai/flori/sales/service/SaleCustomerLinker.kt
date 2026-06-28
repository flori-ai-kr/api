package kr.ai.flori.sales.service

import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.customers.repository.CustomerRepository
import kr.ai.flori.customers.service.CustomerService
import org.springframework.stereotype.Component

/**
 * 매출↔고객 연결 해석. 고객 도메인 접근을 한곳에 모은다(도메인 경계 준수).
 */
@Component
class SaleCustomerLinker(
    private val customerRepository: CustomerRepository,
    private val customerService: CustomerService,
) {
    /** 고객 도메인 리포지토리를 통해 소유권 검증(raw SQL 대신 도메인 경계 준수). */
    fun verifyOwnership(
        userId: Long,
        customerId: Long,
    ) {
        if (customerRepository.findByIdAndUserId(customerId, userId) == null) {
            throw AppException(CommonErrorCode.VALIDATION, "유효하지 않은 고객입니다")
        }
    }

    /**
     * 매출에 연결할 고객 id를 해석한다.
     * - customerId가 오면 소유권 검증 후 그대로 사용(드롭다운에서 직접 선택한 경우)
     * - customerId가 없고 이름·전화가 있으면 전화번호 기준 findOrCreate로 연결(자동 매칭)
     *   → 예약/매출 등록 시 제안 고객을 클릭하지 않아도 전화번호로 기존 고객과 이어진다.
     */
    fun resolve(
        userId: Long,
        customerId: Long?,
        customerName: String?,
        customerPhone: String?,
    ): Long? {
        if (customerId != null) {
            verifyOwnership(userId, customerId)
            return customerId
        }
        val phone = customerPhone?.takeIf { it.isNotBlank() }
        val name = customerName?.takeIf { it.isNotBlank() }
        return if (phone != null && name != null) customerService.findOrCreate(name, phone).id else null
    }
}
