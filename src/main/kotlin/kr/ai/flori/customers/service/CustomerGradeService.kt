package kr.ai.flori.customers.service

import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.customers.dto.CustomerGradeCreateRequest
import kr.ai.flori.customers.dto.CustomerGradeResponse
import kr.ai.flori.customers.dto.CustomerGradeUpdateRequest
import kr.ai.flori.customers.entity.CustomerGrade
import kr.ai.flori.customers.repository.CustomerGradeRepository
import kr.ai.flori.customers.repository.CustomerQueryRepository
import kr.ai.flori.customers.repository.CustomerRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 고객 등급 CRUD + 등급 정책(자동승급 규칙)의 단일 소유자.
 * "구매횟수 N이면 어떤 등급인가"의 판단(gradeIdFor)과 자동 재계산(recomputeGrade)은 모두 여기서만 한다.
 */
@Service
class CustomerGradeService(
    private val gradeRepository: CustomerGradeRepository,
    private val customerRepository: CustomerRepository,
    private val queryRepository: CustomerQueryRepository,
) {
    // 첫 조회 시 기본 등급을 시드(ensureDefaults)하므로 쓰기 가능 트랜잭션이어야 한다(readOnly 금지).
    @Transactional
    fun list(): List<CustomerGradeResponse> {
        val userId = TenantContext.currentUserId()
        ensureDefaults(userId)
        return gradeRepository.findByUserIdOrderBySortOrderAsc(userId).map(CustomerGradeResponse::from)
    }

    @Transactional
    fun create(req: CustomerGradeCreateRequest): CustomerGradeResponse {
        val userId = TenantContext.currentUserId()
        if (gradeRepository.existsByUserIdAndName(userId, req.name)) {
            throw AppException(CommonErrorCode.CONFLICT, "이미 있는 등급명입니다")
        }
        val nextOrder = (gradeRepository.findByUserIdOrderBySortOrderAsc(userId).maxOfOrNull { it.sortOrder } ?: 0) + 1
        val grade =
            CustomerGrade(userId, req.name).apply {
                threshold = req.threshold
                sortOrder = nextOrder
            }
        return CustomerGradeResponse.from(gradeRepository.save(grade))
    }

    @Transactional
    fun update(
        id: Long,
        req: CustomerGradeUpdateRequest,
    ): CustomerGradeResponse {
        val userId = TenantContext.currentUserId()
        val grade =
            gradeRepository.findByIdAndUserId(id, userId)
                ?: throw AppException(CommonErrorCode.NOT_FOUND, "등급을 찾을 수 없습니다")
        req.name?.let {
            if (gradeRepository.existsByUserIdAndNameAndIdNot(userId, it, id)) {
                throw AppException(CommonErrorCode.CONFLICT, "이미 있는 등급명입니다")
            }
            grade.name = it
        }
        val previousThreshold = grade.threshold
        if (req.clearThreshold) {
            grade.threshold = null
        } else {
            req.threshold?.let { grade.threshold = it }
        }
        req.sortOrder?.let { grade.sortOrder = it }
        val saved = gradeRepository.save(grade)
        // 임계값이 실제로 바뀐 경우에만 기존 고객 등급을 즉시 일괄 재산정(잠금 고객 제외).
        if (saved.threshold != previousThreshold) {
            recomputeAllGrades(userId)
        }
        return CustomerGradeResponse.from(saved)
    }

    @Transactional
    fun delete(id: Long) {
        val userId = TenantContext.currentUserId()
        val grade =
            gradeRepository.findByIdAndUserId(id, userId)
                ?: throw AppException(CommonErrorCode.NOT_FOUND, "등급을 찾을 수 없습니다")
        if (gradeRepository.countByUserId(userId) <= 1) {
            throw AppException(CommonErrorCode.VALIDATION, "최소 1개 등급은 있어야 합니다")
        }
        // 이 등급을 참조하던 고객의 grade_id를 NULL 처리(소프트 참조, 순환주입 회피).
        // 실제 등급 재계산은 이후 매출 변경/되돌리기 경로(Task 5/7)에서 수행.
        customerRepository.clearGradeReference(userId, id)
        gradeRepository.delete(grade)
    }

    /** 구매횟수 기준 적정 등급 id. threshold 있는 등급 중 threshold<=count 최대, 미달이면 fallback. */
    fun gradeIdFor(
        userId: Long,
        purchaseCount: Int,
    ): Long? = resolveGradeId(gradeRepository.findByUserIdOrderBySortOrderAsc(userId), purchaseCount)

    /**
     * 등급 목록과 구매횟수로 적정 등급 id를 고른다(순수 함수).
     * threshold<=count 인 등급 중 threshold 최대값, 모든 임계값 미만이면 fallback:
     * threshold가 가장 낮은(non-null) 등급(예: 신규 0회), 전부 null이면 최저 sort_order.
     */
    private fun resolveGradeId(
        grades: List<CustomerGrade>,
        purchaseCount: Int,
    ): Long? {
        if (grades.isEmpty()) return null
        val eligible = grades.filter { it.threshold != null && it.threshold!! <= purchaseCount }.maxByOrNull { it.threshold!! }
        val fallback =
            grades.filter { it.threshold != null }.minByOrNull { it.threshold!! }
                ?: grades.minByOrNull { it.sortOrder }
        return (eligible ?: fallback)?.id
    }

    /** 자동 등급 재계산(잠금 아니면). 매출 변경/되돌리기 후 호출. */
    @Transactional
    fun recomputeGrade(customerId: Long) {
        val userId = TenantContext.currentUserId()
        val customer = customerRepository.findByIdAndUserId(customerId, userId) ?: return
        if (customer.gradeLocked) return
        val count = queryRepository.statsFor(userId, customerId).count
        val newGradeId = gradeIdFor(userId, count)
        if (newGradeId != null && newGradeId != customer.gradeId) {
            customer.gradeId = newGradeId
            customerRepository.save(customer)
        }
    }

    /**
     * 임계값 변경 시 해당 테넌트의 잠금 아닌 고객 전원을 일괄 재산정(변경분만 저장).
     * 구매횟수는 1쿼리 bulk 집계(purchaseCounts), 등급 목록도 1회만 로드해 고객별 재조회를 피한다.
     */
    @Transactional
    fun recomputeAllGrades(userId: Long) {
        val grades = gradeRepository.findByUserIdOrderBySortOrderAsc(userId)
        if (grades.isEmpty()) return
        val counts = queryRepository.purchaseCounts(userId)
        customerRepository.findByUserIdAndGradeLockedFalse(userId).forEach { customer ->
            val count = counts[customer.id] ?: 0
            val newGradeId = resolveGradeId(grades, count)
            if (newGradeId != null && newGradeId != customer.gradeId) {
                customer.gradeId = newGradeId
                customerRepository.save(customer)
            }
        }
    }

    /** 등급이 하나도 없는 테넌트에 기본 등급 4종 생성(신규0/단골5/VIP10/블랙리스트 수동). */
    @Transactional
    fun ensureDefaults(userId: Long) {
        // 빠른 경로: 이미 등급이 있으면 즉시 반환.
        if (gradeRepository.countByUserId(userId) > 0L) return
        // 동시 첫 요청(레이스 컨디션)에서 두 번째 스레드가 UNIQUE(user_id, name) 제약을 위반할 수 있다.
        // 해당 예외는 다른 스레드가 이미 시드를 완료했다는 의미이므로 조용히 무시한다.
        try {
            DEFAULT_GRADES.forEachIndexed { i, (n, t) ->
                gradeRepository.save(
                    CustomerGrade(userId, n).apply {
                        threshold = t
                        sortOrder = i + 1
                    },
                )
            }
            gradeRepository.save(
                CustomerGrade(userId, "블랙리스트").apply {
                    threshold = null
                    sortOrder = BLACKLIST_SORT_ORDER
                },
            )
        } catch (_: DataIntegrityViolationException) {
            // 다른 스레드가 동시에 시드를 완료한 경우 — 무시하고 진행.
        }
    }

    companion object {
        /** 가입 시 자동 생성되는 기본 등급(등급명 to 자동 승급 임계 구매횟수). */
        private val DEFAULT_GRADES = listOf("신규" to 0, "단골" to 5, "VIP" to 10)

        /** 블랙리스트(수동 전용)의 정렬 순서 — DEFAULT_GRADES 뒤. */
        private const val BLACKLIST_SORT_ORDER = 4
    }
}
