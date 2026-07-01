package kr.ai.flori.billing.service

import kr.ai.flori.billing.client.BillingClient
import kr.ai.flori.billing.dto.CardChangeRequest
import kr.ai.flori.billing.dto.MeResponse
import kr.ai.flori.billing.dto.PaymentSummary
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
import kr.ai.flori.billing.repository.PaymentHistoryRepository
import kr.ai.flori.billing.repository.SubscriptionEligibilityRepository
import kr.ai.flori.billing.repository.SubscriptionRepository
import kr.ai.flori.billing.support.BillingPeriods
import kr.ai.flori.billing.support.IdentityHasher
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.verification.error.VerificationErrorCode
import kr.ai.flori.verification.service.BusinessVerificationService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZonedDateTime
import java.util.UUID

@Service
class SubscriptionService(
    private val subscriptionRepository: SubscriptionRepository,
    private val billingKeyRepository: BillingKeyRepository,
    private val eligibilityRepository: SubscriptionEligibilityRepository,
    private val redemptionRepository: CouponRedemptionRepository,
    private val businessVerificationService: BusinessVerificationService,
    private val billingClient: BillingClient,
    private val identityHasher: IdentityHasher,
    private val eventPublisher: ApplicationEventPublisher,
    private val paymentHistoryRepository: PaymentHistoryRepository,
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

        // 체험 자격(사업자등록번호 기준 — 소셜 신원 우회 방지)
        val elig = resolveEligibility(userId)
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

    /**
     * 카드 없이 무료체험 시작(토스 PG 미준비 출시 대응). billingKeyId=null로 TRIALING 생성하고,
     * nextBillingAt 도래 시 스케줄러가 과금 대신 EXPIRED 처리한다. 카드 등록/결제는 체험 종료 후
     * 기존 결제벽(subscribe/checkout)에서만 받는다.
     *
     * 멱등: 이미 구독이 있으면 그 구독을 그대로 반환(중복 생성 금지).
     * 자격: 사업자등록번호 해시 기준 1회. APPROVED 인증이 없으면 가드(NOT_VERIFIED).
     */
    @Transactional
    fun startTrial(): SubscriptionResponse {
        val userId = TenantContext.currentUserId()
        // 멱등 — 어떤 상태든 구독이 이미 있으면 그대로 반환
        subscriptionRepository.findByUserId(userId)?.let {
            return SubscriptionResponse.of(it, billingKeyRepository.findByUserId(userId))
        }

        val elig = resolveEligibility(userId)
        if (elig.trialUsedAt != null) {
            throw AppException(BillingErrorCode.TRIAL_ALREADY_USED)
        }

        val plan = "MONTHLY" // placeholder — 실제 플랜은 체험 종료 결제벽에서 선택
        val amount = amountFor(plan)
        val nowKst = ZonedDateTime.now(KST)
        val now = nowKst.toInstant()
        val periodEnd = nowKst.plusDays(TRIAL_DAYS).toInstant()

        elig.trialUsedAt = now
        eligibilityRepository.save(elig)

        val sub =
            Subscription(userId, plan, "TRIALING", amount, periodEnd).apply {
                billingKeyId = null // 무카드 체험
                currentPeriodStart = now
                currentPeriodEnd = periodEnd
                cancelAtPeriodEnd = false
            }
        val savedSub = subscriptionRepository.save(sub)

        subscriptionRepository.flush()
        eventPublisher.publishEvent(
            SubscriptionStartedEvent(userId, savedSub.id!!, plan, amount, trial = true),
        )
        return SubscriptionResponse.of(savedSub, null)
    }

    /**
     * 체험 1회 제한 자격 행을 사업자등록번호 해시로 해석한다(없으면 새 행). APPROVED 인증이 없으면 가드.
     * 게이트가 APPROVED를 보장하지만 방어적으로 검사한다.
     */
    private fun resolveEligibility(userId: Long): SubscriptionEligibility {
        val businessNumber =
            businessVerificationService.approvedBusinessNumber(userId)
                ?: throw AppException(VerificationErrorCode.NOT_VERIFIED)
        val idHash = identityHasher.hashBusiness(businessNumber)
        return eligibilityRepository.findByIdentityHash(idHash) ?: SubscriptionEligibility(idHash)
    }

    /** me 응답용: 무카드 체험 시작 가능 여부. 구독이 없고 사업자번호 기준 체험 미사용일 때만 true(미인증이면 false). */
    private fun computeTrialEligible(
        userId: Long,
        sub: Subscription?,
    ): Boolean {
        if (sub != null) return false
        val businessNumber = businessVerificationService.approvedBusinessNumber(userId) ?: return false
        val idHash = identityHasher.hashBusiness(businessNumber)
        return eligibilityRepository.findByIdentityHash(idHash)?.trialUsedAt == null
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
        }
        redemptionRepository.saveAll(pending)
        sub.nextBillingAt = next.toInstant()
        sub.currentPeriodEnd = next.toInstant()
        subscriptionRepository.save(sub)
    }

    @Transactional(readOnly = true)
    fun me(): MeResponse {
        val userId = TenantContext.currentUserId()
        val sub = subscriptionRepository.findByUserId(userId)
        val card = billingKeyRepository.findByUserId(userId)
        val payments =
            sub?.id?.let {
                paymentHistoryRepository.findTop10BySubscriptionIdOrderByCreatedAtDesc(it).map(PaymentSummary::from)
            } ?: emptyList()
        return MeResponse(
            subscription = sub?.let { SubscriptionResponse.of(it, card) },
            recentPayments = payments,
            trialEligible = computeTrialEligible(userId, sub),
        )
    }

    @Transactional
    fun cancel(): SubscriptionResponse = setCancel(true)

    @Transactional
    fun resume(): SubscriptionResponse = setCancel(false)

    private fun setCancel(flag: Boolean): SubscriptionResponse {
        val userId = TenantContext.currentUserId()
        val sub =
            subscriptionRepository.findByUserId(userId)
                ?: throw AppException(BillingErrorCode.SUBSCRIPTION_STATE, "구독이 없습니다")
        if (sub.status !in ACTIVE_STATES) throw AppException(BillingErrorCode.SUBSCRIPTION_STATE, "해지/재개할 수 없는 상태입니다")
        sub.cancelAtPeriodEnd = flag
        return SubscriptionResponse.of(subscriptionRepository.save(sub), billingKeyRepository.findByUserId(userId))
    }

    @Transactional
    fun changeCard(req: CardChangeRequest): SubscriptionResponse {
        val userId = TenantContext.currentUserId()
        val sub =
            subscriptionRepository.findByUserId(userId)
                ?: throw AppException(BillingErrorCode.SUBSCRIPTION_STATE, "구독이 없습니다")
        val issued = billingClient.issueBillingKey(req.authKey, req.customerKey)
        // 카드(빌링키) upsert — 무카드 체험자는 카드가 없으므로 신규 생성, 기존 카드가 있으면 교체.
        val card =
            (billingKeyRepository.findByUserId(userId) ?: BillingKey(userId, req.customerKey, issued.billingKey)).apply {
                customerKey = req.customerKey
                billingKey = issued.billingKey
                issued.cardCompany?.let { cardCompany = it }
                cardNumberMasked = issued.cardNumber
                cardType = issued.cardType
                status = "ACTIVE"
            }
        val savedCard = billingKeyRepository.save(card)
        // 무카드 체험 중 첫 카드 등록: 구독에 빌링키를 연결하면 체험 종료 시 만료 대신 자동 결제로 전환된다.
        // 남은 체험 기간·다음 결제일은 그대로 유지(즉시 과금하지 않음).
        if (sub.billingKeyId == null) {
            sub.billingKeyId = savedCard.id
            subscriptionRepository.save(sub)
        }
        return SubscriptionResponse.of(sub, savedCard)
    }

    /** 무료 일수 적용: 활성 구독이면 nextBillingAt/period_end를 days만큼 밀고 갱신된 Subscription 반환. 없거나 비활성이면 null(pending). */
    @Transactional
    fun applyFreeDays(
        userId: Long,
        days: Int,
    ): Subscription? {
        val sub = subscriptionRepository.findByUserId(userId) ?: return null
        if (sub.status !in ACTIVE_STATES) return null
        val next = ZonedDateTime.ofInstant(sub.nextBillingAt, KST).plusDays(days.toLong())
        sub.nextBillingAt = next.toInstant()
        sub.currentPeriodEnd = next.toInstant()
        return subscriptionRepository.save(sub)
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
    ): Instant = BillingPeriods.next(plan, from).toInstant()

    companion object {
        val KST = BillingPeriods.KST
        const val TRIAL_DAYS = 30L
        const val MONTHLY_AMOUNT = 14900
        const val YEARLY_AMOUNT = 154800
        val ACTIVE_STATES = setOf("TRIALING", "ACTIVE", "IN_GRACE")
    }
}
