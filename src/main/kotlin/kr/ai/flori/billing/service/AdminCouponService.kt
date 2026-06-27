package kr.ai.flori.billing.service

import kr.ai.flori.admin.service.AdminAuditService
import kr.ai.flori.billing.dto.CouponDetailResponse
import kr.ai.flori.billing.dto.CouponIssueRequest
import kr.ai.flori.billing.dto.CouponResponse
import kr.ai.flori.billing.dto.CouponUpdateRequest
import kr.ai.flori.billing.dto.RedemptionRow
import kr.ai.flori.billing.entity.Coupon
import kr.ai.flori.billing.repository.CouponRedemptionRepository
import kr.ai.flori.billing.repository.CouponRepository
import kr.ai.flori.billing.support.CouponCodeGenerator
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.user.repository.UserProfileRepository
import kr.ai.flori.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class AdminCouponService(
    private val couponRepository: CouponRepository,
    private val redemptionRepository: CouponRedemptionRepository,
    private val codeGenerator: CouponCodeGenerator,
    private val auditService: AdminAuditService,
    private val userRepository: UserRepository,
    private val userProfileRepository: UserProfileRepository,
) {
    @Transactional
    fun issue(request: CouponIssueRequest): CouponResponse {
        // 코드는 대문자로 통일 저장(자동생성 코드도 대문자). redeem도 대문자 정규화해 대소문자 무관 일치.
        val upperManual = request.code?.trim()?.uppercase()
        val code = upperManual?.takeIf { it.isNotBlank() } ?: codeGenerator.generate()
        if (couponRepository.existsByCode(code)) throw AppException(CommonErrorCode.CONFLICT, "이미 존재하는 코드입니다")
        val coupon =
            Coupon(code = code, days = request.days).apply {
                validFrom = request.validFrom
                validUntil = request.validUntil
                maxRedemptions = request.maxRedemptions
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

    @Transactional(readOnly = true)
    fun detail(id: Long): CouponDetailResponse {
        val coupon = couponRepository.findById(id).orElseThrow { AppException(CommonErrorCode.NOT_FOUND, "쿠폰을 찾을 수 없습니다") }
        val redemptions = redemptionRepository.findByCouponIdOrderByCreatedAtDesc(id)
        // 사용현황 유저정보(닉네임·가게명) 배치 조회 — N+1 회피. 매핑 없으면 null.
        val userIds = redemptions.map { it.userId }.distinct()
        val nicknameById = userRepository.findAllById(userIds).associate { it.id to it.nickname }
        val storeNameById = userProfileRepository.findAllById(userIds).associate { it.userId to it.storeName }
        val rows =
            redemptions.map {
                RedemptionRow(
                    userId = it.userId,
                    grantedDays = it.grantedDays,
                    redeemedAt = it.createdAt,
                    nickname = nicknameById[it.userId],
                    storeName = storeNameById[it.userId],
                )
            }
        return CouponDetailResponse(CouponResponse.of(coupon, Instant.now()), rows)
    }

    /**
     * 쿠폰 수정. code·source는 불변(식별자/용도)이라 요청에 없고 기존 값을 유지한다.
     * days 변경은 이후 사용분에만 영향 — 기존 CouponRedemption.grantedDays 스냅샷은 불변.
     */
    @Transactional
    fun update(
        id: Long,
        request: CouponUpdateRequest,
    ): CouponResponse {
        val coupon = couponRepository.findById(id).orElseThrow { AppException(CommonErrorCode.NOT_FOUND, "쿠폰을 찾을 수 없습니다") }
        coupon.days = request.days
        coupon.validFrom = request.validFrom
        coupon.validUntil = request.validUntil
        coupon.maxRedemptions = request.maxRedemptions
        coupon.memo = request.memo
        val saved = couponRepository.save(coupon)
        auditService.record(
            action = "COUPON_UPDATE",
            targetType = "coupon",
            targetId = id.toString(),
            summary = "쿠폰 수정 ${coupon.code} (${request.days}일)",
            metadata =
                mapOf(
                    "days" to request.days,
                    "maxRedemptions" to request.maxRedemptions,
                ),
        )
        return CouponResponse.of(saved, Instant.now())
    }

    @Transactional
    fun disable(id: Long): CouponResponse {
        val coupon = couponRepository.findById(id).orElseThrow { AppException(CommonErrorCode.NOT_FOUND, "쿠폰을 찾을 수 없습니다") }
        coupon.status = "DISABLED"
        val saved = couponRepository.save(coupon)
        auditService.record(
            action = "COUPON_DISABLE",
            targetType = "coupon",
            targetId = id.toString(),
            summary = "쿠폰 폐기 ${coupon.code}",
        )
        return CouponResponse.of(saved, Instant.now())
    }
}
