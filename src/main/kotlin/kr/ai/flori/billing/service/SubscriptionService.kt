package kr.ai.flori.billing.service

import kr.ai.flori.billing.client.BillingClient
import kr.ai.flori.billing.dto.PrepareResponse
import kr.ai.flori.billing.dto.SubscribeRequest
import kr.ai.flori.billing.dto.SubscriptionResponse
import kr.ai.flori.billing.entity.BillingKey
import kr.ai.flori.billing.entity.Subscription
import kr.ai.flori.billing.entity.SubscriptionEligibility
import kr.ai.flori.billing.error.BillingErrorCode
import kr.ai.flori.billing.event.SubscriptionStartedEvent
import kr.ai.flori.billing.repository.BillingKeyRepository
import kr.ai.flori.billing.repository.CouponRedemptionRepository
import kr.ai.flori.billing.repository.SubscriptionEligibilityRepository
import kr.ai.flori.billing.repository.SubscriptionRepository
import kr.ai.flori.billing.support.IdentityHasher
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.user.repository.UserRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

@Service
class SubscriptionService(
    private val subscriptionRepository: SubscriptionRepository,
    private val billingKeyRepository: BillingKeyRepository,
    private val eligibilityRepository: SubscriptionEligibilityRepository,
    private val redemptionRepository: CouponRedemptionRepository,
    private val userRepository: UserRepository,
    private val billingClient: BillingClient,
    private val identityHasher: IdentityHasher,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional(readOnly = true)
    fun prepare(): PrepareResponse {
        val userId = TenantContext.currentUserId()
        val existing = billingKeyRepository.findByUserId(userId)?.customerKey
        return PrepareResponse(existing ?: UUID.randomUUID().toString())
    }

    /**
     * 빌링키 발급(외부 토스 호출)을 트랜잭션 맨 앞에서 수행하고, 이후 영속 작업 전체를 동일 트랜잭션에서 처리한다.
     * self-invocation(@Transactional protected fun persist()) 방식은 Kotlin/Spring 프록시에서
     * 트랜잭션이 걸리지 않으므로 채택하지 않는다. issueBillingKey 실패 시 트랜잭션이 롤백된다.
     */
    @Transactional
    fun subscribe(req: SubscribeRequest): SubscriptionResponse {
        val userId = TenantContext.currentUserId()
        guardNotAlreadyActive(userId)
        val amount = amountFor(req.plan)

        // 외부 호출: 실패 시 트랜잭션 전체 롤백 (DB 변경 없으므로 안전)
        val issued = billingClient.issueBillingKey(req.authKey, req.customerKey)

        // 카드(빌링키) upsert — user_id UNIQUE
        val card =
            (billingKeyRepository.findByUserId(userId) ?: BillingKey(userId, req.customerKey, issued.billingKey)).apply {
                customerKey = req.customerKey
                billingKey = issued.billingKey
                issued.cardCompany?.let { this.cardCompany = it }
                cardNumberMasked = issued.cardNumber
                this.cardType = issued.cardType
                status = "ACTIVE"
            }
        val savedCard = billingKeyRepository.save(card)

        // 체험 자격(신원 기준)
        val user = userRepository.findById(userId).orElseThrow { AppException(CommonErrorCode.UNAUTHORIZED) }
        val idHash = identityHasher.hash(user.provider, user.providerId)
        val elig = eligibilityRepository.findByIdentityHash(idHash) ?: SubscriptionEligibility(idHash)
        val trialEligible = elig.trialUsedAt == null

        val nowKst = ZonedDateTime.now(KST)
        val now = nowKst.toInstant()
        val periodEnd: Instant
        val nextBilling: Instant
        val status: String
        if (trialEligible) {
            status = "TRIALING"
            periodEnd = nowKst.plusDays(TRIAL_DAYS).toInstant()
            nextBilling = periodEnd
            elig.trialUsedAt = now
            eligibilityRepository.save(elig)
        } else {
            status = "ACTIVE"
            periodEnd = periodEndFor(req.plan, nowKst)
            nextBilling = now // 스케줄러가 ≤1일 내 첫 과금
        }

        // 구독 upsert — user_id UNIQUE
        val sub =
            (subscriptionRepository.findByUserId(userId) ?: Subscription(userId, req.plan, status, amount, nextBilling)).apply {
                plan = req.plan
                this.status = status
                this.amount = amount
                billingKeyId = savedCard.id
                currentPeriodStart = now
                currentPeriodEnd = periodEnd
                nextBillingAt = nextBilling
                cancelAtPeriodEnd = false
                graceUntil = null
                retryCount = 0
            }
        val savedSub = subscriptionRepository.save(sub)

        // pending 쿠폰 적용(가입 전 redeem분) — Part 4 전엔 빈 리스트
        applyPendingCoupons(userId, savedSub)

        subscriptionRepository.flush()
        eventPublisher.publishEvent(
            SubscriptionStartedEvent(userId, savedSub.id!!, req.plan, amount, trial = trialEligible),
        )
        return SubscriptionResponse.of(savedSub, savedCard)
    }

    private fun applyPendingCoupons(
        userId: Long,
        sub: Subscription,
    ) {
        val pending = redemptionRepository.findByUserIdAndSubscriptionIdIsNull(userId)
        if (pending.isEmpty()) return
        var next = ZonedDateTime.ofInstant(sub.nextBillingAt, KST)
        pending.forEach { r ->
            next = next.plusDays(r.grantedDays.toLong())
            r.subscriptionId = sub.id
            redemptionRepository.save(r)
        }
        sub.nextBillingAt = next.toInstant()
        sub.currentPeriodEnd = next.toInstant()
        subscriptionRepository.save(sub)
    }

    private fun guardNotAlreadyActive(userId: Long) {
        val existing = subscriptionRepository.findByUserId(userId) ?: return
        if (existing.status in ACTIVE_STATES) {
            throw AppException(BillingErrorCode.SUBSCRIPTION_STATE, "이미 구독 중입니다")
        }
    }

    private fun amountFor(plan: String): Int =
        when (plan) {
            "MONTHLY" -> MONTHLY_AMOUNT
            "YEARLY" -> YEARLY_AMOUNT
            else -> throw AppException(CommonErrorCode.VALIDATION, "알 수 없는 플랜입니다")
        }

    private fun periodEndFor(
        plan: String,
        from: ZonedDateTime,
    ): Instant =
        when (plan) {
            "MONTHLY" -> from.plusMonths(1).toInstant()
            "YEARLY" -> from.plusYears(1).toInstant()
            else -> throw AppException(CommonErrorCode.VALIDATION, "알 수 없는 플랜입니다")
        }

    companion object {
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
        const val TRIAL_DAYS = 14L
        const val MONTHLY_AMOUNT = 14900
        const val YEARLY_AMOUNT = 154800
        val ACTIVE_STATES = setOf("TRIALING", "ACTIVE", "IN_GRACE")
    }
}
