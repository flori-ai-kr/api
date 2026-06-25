package kr.ai.flori.billing.service

import kr.ai.flori.admin.service.AdminAuditService
import kr.ai.flori.billing.dto.CouponIssueRequest
import kr.ai.flori.billing.dto.CouponResponse
import kr.ai.flori.billing.entity.Coupon
import kr.ai.flori.billing.repository.CouponRepository
import kr.ai.flori.billing.support.CouponCodeGenerator
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.tenant.TenantContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class AdminCouponService(
    private val couponRepository: CouponRepository,
    private val codeGenerator: CouponCodeGenerator,
    private val auditService: AdminAuditService,
) {
    @Transactional
    fun issue(request: CouponIssueRequest): CouponResponse {
        val code = request.code?.trim()?.takeIf { it.isNotBlank() } ?: codeGenerator.generate()
        if (couponRepository.existsByCode(code)) throw AppException(CommonErrorCode.CONFLICT, "이미 존재하는 코드입니다")
        val coupon =
            Coupon(code = code, days = request.days).apply {
                validFrom = request.validFrom
                validUntil = request.validUntil
                maxRedemptions = request.maxRedemptions
                perUserLimit = request.perUserLimit
                source = request.source
                memo = request.memo
                createdBy = TenantContext.currentUserId()
            }
        val saved = couponRepository.save(coupon)
        auditService.record(
            action = "COUPON_ISSUE",
            targetType = "coupon",
            targetId = saved.id.toString(),
            summary = "쿠폰 발행 $code (${request.days}일)",
            metadata = mapOf("code" to code, "days" to request.days, "maxRedemptions" to request.maxRedemptions),
        )
        return CouponResponse.of(saved, Instant.now())
    }

    @Transactional(readOnly = true)
    fun list(): List<CouponResponse> {
        val now = Instant.now()
        return couponRepository.findAll().sortedByDescending { it.id }.map { CouponResponse.of(it, now) }
    }
}
