package kr.ai.flori.customers.service

import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.customers.dto.CustomerGradeCreateRequest
import kr.ai.flori.customers.dto.CustomerGradeResponse
import kr.ai.flori.customers.dto.CustomerGradeUpdateRequest
import kr.ai.flori.customers.entity.CustomerGrade
import kr.ai.flori.customers.repository.CustomerGradeRepository
import kr.ai.flori.customers.repository.CustomerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CustomerGradeService(
    private val gradeRepository: CustomerGradeRepository,
    private val customerRepository: CustomerRepository,
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
        if (req.clearThreshold) {
            grade.threshold = null
        } else {
            req.threshold?.let { grade.threshold = it }
        }
        req.sortOrder?.let { grade.sortOrder = it }
        return CustomerGradeResponse.from(gradeRepository.save(grade))
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

    /** 등급이 하나도 없는 테넌트에 기본 등급 4종 생성(신규0/단골5/VIP10/블랙리스트 수동). */
    @Transactional
    fun ensureDefaults(userId: Long) {
        if (gradeRepository.countByUserId(userId) > 0L) return
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
    }

    companion object {
        /** 가입 시 자동 생성되는 기본 등급(등급명 to 자동 승급 임계 구매횟수). */
        private val DEFAULT_GRADES = listOf("신규" to 0, "단골" to 5, "VIP" to 10)

        /** 블랙리스트(수동 전용)의 정렬 순서 — DEFAULT_GRADES 뒤. */
        private const val BLACKLIST_SORT_ORDER = 4
    }
}
