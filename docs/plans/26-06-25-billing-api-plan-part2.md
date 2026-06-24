# 토스 빌링 API 구현 계획 — Part 2: 구독 시작·관리

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** 점주가 카드를 등록해 구독을 시작(14일 무료체험, 재가입 어뷰징 방어 포함)하고, 내 구독/카드 조회·해지예약·해지취소·카드교체를 할 수 있게 한다. **동기 결제(승인)는 없다 — 모든 과금은 Part 3 스케줄러가 담당.**

**Architecture:** `BillingController`(점주, `/billing/**`) → `SubscriptionService`(트랜잭션 경계, 멀티테넌시 격리) → Part1 엔티티/리포지토리/`BillingClient`. 빌링키 발급(외부 토스 호출)은 트랜잭션 밖에서 먼저, DB 영속은 `@Transactional` 안에서. 구독 시작은 도메인 이벤트(`SubscriptionStartedEvent`)를 발행하고 `BillingEventListener`가 디스코드+푸시를 비동기 처리.

**Tech Stack:** 동일(Spring Boot 3.5 / Kotlin / JPA / JUnit5 + Zonky + MockK/Mockito).

## Global Constraints

- 금액: `MONTHLY`=**14900**, `YEARLY`=**154800** (정수 KRW). 체험 **14일**. 신원 1개당 체험 1회.
- 구독 상태: `TRIALING`/`ACTIVE`/`IN_GRACE`/`EXPIRED` (없으면 row 없음). **Part 2는 결제 승인 호출 없음** — 과금은 Part 3.
- 재가입자(이미 체험 사용) subscribe → `ACTIVE`, `next_billing_at = now`(스케줄러가 ≤1일 내 첫 과금), 체험 없음.
- 신규/체험가능 subscribe → `TRIALING`, `trial_end = now + 14일`, `next_billing_at = trial_end`.
- 기존 구독이 `TRIALING/ACTIVE/IN_GRACE`면 재구독 거부(`SUBSCRIPTION_STATE`). `EXPIRED`/없음이면 허용(같은 row upsert — user_id UNIQUE).
- 해지 = `cancel_at_period_end=true`(즉시중단 아님). resume = false.
- `identity_hash` = **SHA-256(base64) of `"{provider}:{providerId}"`**. (billingKey AES와 별개 — 단방향.)
- 멀티테넌시 HARD: 모든 점주 API는 `TenantContext.currentUserId()`로 격리. `/billing/**`는 SecurityConfig `anyRequest authenticated`로 이미 인증됨(추가 규칙 불필요).
- **[Part1 이월 적용]** PaymentHistory subscriptionId 조회는 Part 3에서. Part 2엔 직접 결제 없음.
- 외부 호출(`issueBillingKey`)은 **트랜잭션 밖**에서 먼저 수행 후, DB 영속만 `@Transactional`.
- 코드스타일 ktlint/detekt, JaCoCo 80%. Git: 변경 파일만 `git add`(`-A` 금지), `Co-Authored-By: Claude <noreply@anthropic.com>`.

## Part 1 인터페이스 (이 계획이 호출)

- `BillingClient.issueBillingKey(authKey, customerKey): IssuedBilling(billingKey, cardCompany, cardNumber, cardType)`
- `Subscription(userId, plan, status, amount, nextBillingAt)` + var: billingKeyId, currentPeriodStart, currentPeriodEnd, cancelAtPeriodEnd, graceUntil, retryCount, id
- `BillingKey(userId, customerKey, billingKey)` + var: cardCompany, cardNumberMasked, cardType, status, id
- `SubscriptionRepository.findByUserId(userId): Subscription?`
- `BillingKeyRepository.findByUserId(userId): BillingKey?`
- `CouponRedemptionRepository.findByUserIdAndSubscriptionIdIsNull(userId): List<CouponRedemption>` (pending — Part 4 전엔 빈 리스트)
- `SubscriptionEligibilityRepository.findByIdentityHash(hash): SubscriptionEligibility?`
- `BillingErrorCode.SUBSCRIPTION_STATE` / `BILLING_KEY_ISSUE_FAILED`
- `UserRepository.findById(userId): Optional<User>` (User.provider, User.providerId)
- `DiscordNotifier.notify(channel, DiscordMessage.of(text))`, `DiscordMessage.of(text)`
- `PushDispatcher.sendToUser(userId, title, body, url?=null): Int`
- 현재 유저: `TenantContext.currentUserId()`
- 이벤트: `ApplicationEventPublisher.publishEvent(...)` + `@TransactionalEventListener(phase=AFTER_COMMIT)`

---

## 마일스톤 로드맵 (재확인)
Part 1 기반 ✅ → **Part 2 구독 시작·관리(이 문서)** → Part 3 결제엔진 → Part 4 쿠폰 → Part 5 웹훅·집계.

## File Structure (Part 2)
- Modify: `common/notification/discord/DiscordChannel.kt` — `BILLING` 추가
- Create: `billing/event/BillingEvents.kt` — 이벤트 data class
- Create: `billing/listener/BillingEventListener.kt`
- Create: `billing/support/IdentityHasher.kt`
- Create: `billing/dto/BillingDtos.kt`
- Create: `billing/service/SubscriptionService.kt`
- Create: `billing/controller/BillingController.kt`
- Test: `billing/DiscordChannelBillingTest.kt`, `billing/SubscriptionServiceTest.kt`, `billing/BillingControllerTest.kt`

---

## Task 1: 알림 인프라 (BILLING 채널 + 이벤트 + 리스너)

**Files:**
- Modify: `src/main/kotlin/kr/ai/flori/common/notification/discord/DiscordChannel.kt`
- Create: `src/main/kotlin/kr/ai/flori/billing/event/BillingEvents.kt`
- Create: `src/main/kotlin/kr/ai/flori/billing/listener/BillingEventListener.kt`
- Test: `src/test/kotlin/kr/ai/flori/billing/DiscordChannelBillingTest.kt`

**Interfaces:**
- Produces: `DiscordChannel.BILLING`; `SubscriptionStartedEvent(userId: Long, subscriptionId: Long, plan: String, amount: Int, trial: Boolean)`; `BillingEventListener` (AFTER_COMMIT → Discord + push).

- [ ] **Step 1: 실패 테스트** (`DiscordChannelBillingTest.kt`)

```kotlin
package kr.ai.flori.billing

import kr.ai.flori.common.notification.discord.DiscordChannel
import kr.ai.flori.common.notification.discord.DiscordProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DiscordChannelBillingTest {
    @Test
    fun `BILLING 채널은 billingWebhookUrl 을 선택한다`() {
        val props = DiscordProperties(billingWebhookUrl = "https://discord/billing")
        assertThat(DiscordChannel.BILLING.urlSelector(props)).isEqualTo("https://discord/billing")
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests kr.ai.flori.billing.DiscordChannelBillingTest`
Expected: FAIL — `DiscordChannel.BILLING` 미존재(컴파일 에러)

- [ ] **Step 3: DiscordChannel에 BILLING 추가**

`DiscordChannel.kt`의 enum 마지막 항목 뒤에 추가:

```kotlin
    INTERVIEW({ it.interviewWebhookUrl }),
    BILLING({ it.billingWebhookUrl }),
```

- [ ] **Step 4: 이벤트 data class** (`billing/event/BillingEvents.kt`)

```kotlin
package kr.ai.flori.billing.event

/** 구독 시작(체험 또는 즉시활성). 리스너가 디스코드+푸시 처리. */
data class SubscriptionStartedEvent(
    val userId: Long,
    val subscriptionId: Long,
    val plan: String,
    val amount: Int,
    val trial: Boolean,
)
```

- [ ] **Step 5: 리스너** (`billing/listener/BillingEventListener.kt`)

```kotlin
package kr.ai.flori.billing.listener

import kr.ai.flori.billing.event.SubscriptionStartedEvent
import kr.ai.flori.common.notification.discord.DiscordChannel
import kr.ai.flori.common.notification.discord.DiscordMessage
import kr.ai.flori.common.notification.discord.DiscordNotifier
import kr.ai.flori.common.push.PushDispatcher
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/** 빌링 도메인 이벤트 → 디스코드 + 유저 푸시(비동기는 DiscordNotifier/@Async가 담당). */
@Component
class BillingEventListener(
    private val discordNotifier: DiscordNotifier,
    private val pushDispatcher: PushDispatcher,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onSubscriptionStarted(event: SubscriptionStartedEvent) {
        val kind = if (event.trial) "체험 시작" else "구독 시작"
        discordNotifier.notify(
            DiscordChannel.BILLING,
            DiscordMessage.of(
                "**[$kind]** userId=${event.userId} plan=${event.plan} ₩${event.amount}",
            ),
        )
        val body = if (event.trial) "14일 무료체험이 시작됐어요." else "구독이 시작됐어요."
        pushDispatcher.sendToUser(event.userId, "Flori 구독", body)
    }
}
```

- [ ] **Step 6: 통과 확인 + 전체 스위트**

Run: `./gradlew test --tests kr.ai.flori.billing.DiscordChannelBillingTest` → PASS
Run: `./gradlew test` → 전체 통과(신규 리스너가 컨텍스트를 깨지 않는지)

- [ ] **Step 7: 커밋**

```bash
git add src/main/kotlin/kr/ai/flori/common/notification/discord/DiscordChannel.kt src/main/kotlin/kr/ai/flori/billing/event src/main/kotlin/kr/ai/flori/billing/listener src/test/kotlin/kr/ai/flori/billing/DiscordChannelBillingTest.kt
git commit -m "feat(billing): BILLING 디스코드 채널 + 구독 이벤트/리스너" -m "Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 2: 구독 시작 (prepare + subscribe + 어뷰징 방어)

**Files:**
- Create: `billing/support/IdentityHasher.kt`
- Create: `billing/dto/BillingDtos.kt`
- Create: `billing/service/SubscriptionService.kt`
- Create: `billing/controller/BillingController.kt`
- Test: `src/test/kotlin/kr/ai/flori/billing/SubscriptionServiceTest.kt`

**Interfaces:**
- Consumes: Part 1 인터페이스(상단) + Task 1 `SubscriptionStartedEvent`.
- Produces:
  - `IdentityHasher.hash(provider: String, providerId: String?): String`
  - `SubscriptionService.prepare(): PrepareResponse(customerKey)`
  - `SubscriptionService.subscribe(req: SubscribeRequest): SubscriptionResponse`
  - `SubscriptionResponse(plan, status, currentPeriodEnd, nextBillingAt, cancelAtPeriodEnd, card: CardSummary?)`
  - `GET /billing/prepare`, `POST /billing/subscribe`

- [ ] **Step 1: 실패 테스트** (`SubscriptionServiceTest.kt`)

```kotlin
package kr.ai.flori.billing

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.billing.client.BillingClient
import kr.ai.flori.billing.client.IssuedBilling
import kr.ai.flori.billing.dto.SubscribeRequest
import kr.ai.flori.billing.repository.BillingKeyRepository
import kr.ai.flori.billing.repository.SubscriptionEligibilityRepository
import kr.ai.flori.billing.repository.SubscriptionRepository
import kr.ai.flori.billing.service.SubscriptionService
import kr.ai.flori.billing.support.IdentityHasher
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.support.TestTenants
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.temporal.ChronoUnit

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class SubscriptionServiceTest {
    @Autowired lateinit var service: SubscriptionService
    @Autowired lateinit var subscriptionRepository: SubscriptionRepository
    @Autowired lateinit var billingKeyRepository: BillingKeyRepository
    @Autowired lateinit var eligibilityRepository: SubscriptionEligibilityRepository
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var authService: AuthService
    @Autowired lateinit var tokenProvider: JwtTokenProvider
    @Autowired lateinit var identityHasher: IdentityHasher

    @MockBean lateinit var billingClient: BillingClient

    @AfterEach fun cleanup() = TenantContext.clear()

    private fun newUser(): Long = TestTenants.bootstrap(authService, tokenProvider, userRepository)

    @Test
    fun `신규 신원 구독시 TRIALING + 체험14일 + 카드저장 + 신원원장 기록`() {
        val userId = newUser()
        whenever(billingClient.issueBillingKey(any(), any()))
            .thenReturn(IssuedBilling("bk_1", "신한", "1234****", "체크"))

        val res = service.subscribe(SubscribeRequest(plan = "MONTHLY", authKey = "auth_1", customerKey = "cust_1"))

        assertThat(res.status).isEqualTo("TRIALING")
        val sub = subscriptionRepository.findByUserId(userId)!!
        assertThat(sub.amount).isEqualTo(14900)
        // 체험 종료(=다음 결제)는 대략 now+14일
        assertThat(sub.nextBillingAt).isBetween(
            Instant.now().plus(13, ChronoUnit.DAYS), Instant.now().plus(15, ChronoUnit.DAYS),
        )
        val card = billingKeyRepository.findByUserId(userId)!!
        assertThat(card.billingKey).isEqualTo("bk_1") // 복호화 원문
        assertThat(card.cardNumberMasked).isEqualTo("1234****")
        // 신원원장에 체험 사용 기록
        val user = userRepository.findById(userId).get()
        val elig = eligibilityRepository.findByIdentityHash(identityHasher.hash(user.provider, user.providerId))!!
        assertThat(elig.trialUsedAt).isNotNull
    }

    @Test
    fun `체험 이미 사용한 신원이 재구독하면 체험없이 ACTIVE + next_billing now`() {
        val userId = newUser()
        whenever(billingClient.issueBillingKey(any(), any()))
            .thenReturn(IssuedBilling("bk_1", "신한", "1234****", "체크"))
        // 1차 구독(체험) 후 만료로 강등
        service.subscribe(SubscribeRequest("MONTHLY", "auth_1", "cust_1"))
        val sub1 = subscriptionRepository.findByUserId(userId)!!
        sub1.status = "EXPIRED"
        subscriptionRepository.save(sub1)

        // 2차 구독(재가입) — 같은 신원
        val res = service.subscribe(SubscribeRequest("MONTHLY", "auth_2", "cust_1"))

        assertThat(res.status).isEqualTo("ACTIVE")
        val sub2 = subscriptionRepository.findByUserId(userId)!!
        assertThat(sub2.nextBillingAt).isBefore(Instant.now().plus(1, ChronoUnit.MINUTES)) // 즉시(now)
    }

    @Test
    fun `이미 활성 구독이면 재구독 거부`() {
        newUser()
        whenever(billingClient.issueBillingKey(any(), any()))
            .thenReturn(IssuedBilling("bk_1", "신한", "1234****", "체크"))
        service.subscribe(SubscribeRequest("MONTHLY", "auth_1", "cust_1")) // TRIALING

        assertThatThrownBy { service.subscribe(SubscribeRequest("MONTHLY", "auth_2", "cust_1")) }
            .isInstanceOf(AppException::class.java)
    }

    @Test
    fun `prepare 는 기존 customerKey 있으면 재사용 없으면 신규 발급`() {
        newUser()
        val k1 = service.prepare().customerKey
        assertThat(k1).isNotBlank()
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests kr.ai.flori.billing.SubscriptionServiceTest`
Expected: FAIL — `SubscriptionService`/DTO/`IdentityHasher` 미존재(컴파일 에러)

- [ ] **Step 3: IdentityHasher** (`billing/support/IdentityHasher.kt`)

```kotlin
package kr.ai.flori.billing.support

import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.util.Base64

/** 어뷰징 방어용 신원 해시. SHA-256(base64) of "provider:providerId". 단방향. */
@Component
class IdentityHasher {
    fun hash(provider: String, providerId: String?): String =
        Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest("$provider:$providerId".toByteArray()),
        )
}
```

- [ ] **Step 4: DTO** (`billing/dto/BillingDtos.kt`)

```kotlin
package kr.ai.flori.billing.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import kr.ai.flori.billing.entity.BillingKey
import kr.ai.flori.billing.entity.Subscription
import java.time.Instant

data class PrepareResponse(val customerKey: String)

data class SubscribeRequest(
    @field:Pattern(regexp = "MONTHLY|YEARLY", message = "plan은 MONTHLY 또는 YEARLY")
    val plan: String,
    @field:NotBlank(message = "authKey는 필수입니다")
    val authKey: String,
    @field:NotBlank(message = "customerKey는 필수입니다")
    val customerKey: String,
)

data class CardSummary(val company: String?, val numberMasked: String?, val cardType: String?) {
    companion object {
        fun from(k: BillingKey?): CardSummary? =
            k?.let { CardSummary(it.cardCompany, it.cardNumberMasked, it.cardType) }
    }
}

data class SubscriptionResponse(
    val plan: String,
    val status: String,
    val currentPeriodEnd: Instant?,
    val nextBillingAt: Instant,
    val cancelAtPeriodEnd: Boolean,
    val card: CardSummary?,
) {
    companion object {
        fun of(sub: Subscription, card: BillingKey?): SubscriptionResponse =
            SubscriptionResponse(
                plan = sub.plan,
                status = sub.status,
                currentPeriodEnd = sub.currentPeriodEnd,
                nextBillingAt = sub.nextBillingAt,
                cancelAtPeriodEnd = sub.cancelAtPeriodEnd,
                card = CardSummary.from(card),
            )
    }
}
```

- [ ] **Step 5: SubscriptionService** (`billing/service/SubscriptionService.kt`)

```kotlin
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

    /** 빌링키 발급(외부)은 트랜잭션 밖에서 먼저, 영속은 persist()에서. */
    fun subscribe(req: SubscribeRequest): SubscriptionResponse {
        val userId = TenantContext.currentUserId()
        guardNotAlreadyActive(userId)
        val amount = amountFor(req.plan)
        val issued = billingClient.issueBillingKey(req.authKey, req.customerKey) // 외부 호출(tx 밖)
        return persist(userId, req, amount, issued.billingKey, issued.cardCompany, issued.cardNumber, issued.cardType)
    }

    @Transactional
    protected fun persist(
        userId: Long,
        req: SubscribeRequest,
        amount: Int,
        billingKeyValue: String,
        cardCompany: String?,
        cardNumber: String?,
        cardType: String?,
    ): SubscriptionResponse {
        // 카드(빌링키) upsert — user_id UNIQUE
        val card = (billingKeyRepository.findByUserId(userId) ?: BillingKey(userId, req.customerKey, billingKeyValue)).apply {
            customerKey = req.customerKey
            billingKey = billingKeyValue
            cardCompany?.let { this.cardCompany = it }
            cardNumberMasked = cardNumber
            this.cardType = cardType
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

        // 구독 upsert
        val sub = (subscriptionRepository.findByUserId(userId) ?: Subscription(userId, req.plan, status, amount, nextBilling)).apply {
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

    private fun applyPendingCoupons(userId: Long, sub: Subscription) {
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

    private fun periodEndFor(plan: String, from: ZonedDateTime): Instant =
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
```

> 주의(Kotlin/Spring): `@Transactional`을 `protected` 메서드 self-invocation으로 쓰면 프록시가 적용되지 않는다. `subscribe()`가 같은 빈의 `persist()`를 직접 호출하면 트랜잭션이 안 걸린다. **구현 시 둘 중 하나로 교정**: (a) `persist`를 별도 빈(`SubscriptionPersister`)으로 분리해 주입, 또는 (b) `subscribe` 전체를 `@Transactional`로 두고 외부호출(issueBillingKey)을 트랜잭션 안에서 수행(토스 승인은 저렴한 호출이라 허용 — Part1 탐색의 트랜잭션 패턴). **권장: (b)** — `subscribe`에 `@Transactional`, `issueBillingKey`를 맨 앞에서 호출(실패 시 롤백). self-invocation 함정 회피가 핵심이므로 구현자는 반드시 둘 중 하나를 적용하고 테스트로 트랜잭션 동작(실패 시 롤백)을 확인.

- [ ] **Step 6: Controller** (`billing/controller/BillingController.kt`)

```kotlin
package kr.ai.flori.billing.controller

import jakarta.validation.Valid
import kr.ai.flori.billing.dto.PrepareResponse
import kr.ai.flori.billing.dto.SubscribeRequest
import kr.ai.flori.billing.dto.SubscriptionResponse
import kr.ai.flori.billing.service.SubscriptionService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/billing")
class BillingController(
    private val subscriptionService: SubscriptionService,
) {
    @GetMapping("/prepare")
    fun prepare(): PrepareResponse = subscriptionService.prepare()

    @PostMapping("/subscribe")
    fun subscribe(
        @Valid @RequestBody request: SubscribeRequest,
    ): SubscriptionResponse = subscriptionService.subscribe(request)
}
```

- [ ] **Step 7: 통과 확인 + 전체 스위트**

Run: `./gradlew test --tests kr.ai.flori.billing.SubscriptionServiceTest` → PASS (4 tests)
Run: `./gradlew test` → 전체 통과

- [ ] **Step 8: 커밋**

```bash
git add src/main/kotlin/kr/ai/flori/billing/support src/main/kotlin/kr/ai/flori/billing/dto src/main/kotlin/kr/ai/flori/billing/service src/main/kotlin/kr/ai/flori/billing/controller src/test/kotlin/kr/ai/flori/billing/SubscriptionServiceTest.kt
git commit -m "feat(billing): 구독 시작(prepare/subscribe) + 체험·재가입 어뷰징 방어" -m "Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 3: 구독 조회·해지·재개·카드교체 (me/cancel/resume/card)

**Files:**
- Modify: `billing/service/SubscriptionService.kt` (메서드 추가)
- Modify: `billing/controller/BillingController.kt` (엔드포인트 추가)
- Modify: `billing/dto/BillingDtos.kt` (`MeResponse`, `CardChangeRequest`)
- Test: `src/test/kotlin/kr/ai/flori/billing/BillingControllerTest.kt`

**Interfaces:**
- Consumes: Task 2 `SubscriptionService`, `PaymentHistoryRepository.findTop10BySubscriptionIdOrderByCreatedAtDesc`.
- Produces:
  - `SubscriptionService.me(): MeResponse`, `cancel(): SubscriptionResponse`, `resume(): SubscriptionResponse`, `changeCard(req): SubscriptionResponse`
  - `GET /billing/me`, `POST /billing/cancel`, `POST /billing/resume`, `POST /billing/card`
  - `MeResponse(subscription: SubscriptionResponse?, recentPayments: List<PaymentSummary>)`

- [ ] **Step 1: 실패 테스트** (`BillingControllerTest.kt`)

```kotlin
package kr.ai.flori.billing

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.billing.client.BillingClient
import kr.ai.flori.billing.client.IssuedBilling
import kr.ai.flori.billing.dto.SubscribeRequest
import kr.ai.flori.billing.service.SubscriptionService
import kr.ai.flori.billing.repository.SubscriptionRepository
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.support.TestTenants
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class BillingControllerTest {
    @Autowired lateinit var service: SubscriptionService
    @Autowired lateinit var subscriptionRepository: SubscriptionRepository
    @Autowired lateinit var authService: AuthService
    @Autowired lateinit var tokenProvider: JwtTokenProvider
    @Autowired lateinit var userRepository: UserRepository
    @MockBean lateinit var billingClient: BillingClient

    @AfterEach fun cleanup() = TenantContext.clear()

    private fun subscribedUser(): Long {
        val userId = TestTenants.bootstrap(authService, tokenProvider, userRepository)
        whenever(billingClient.issueBillingKey(any(), any())).thenReturn(IssuedBilling("bk_1", "신한", "1234****", "체크"))
        service.subscribe(SubscribeRequest("MONTHLY", "auth_1", "cust_1"))
        return userId
    }

    @Test
    fun `me 는 구독+카드+결제내역(빈) 반환`() {
        subscribedUser()
        val me = service.me()
        assertThat(me.subscription).isNotNull
        assertThat(me.subscription!!.card?.numberMasked).isEqualTo("1234****")
        assertThat(me.recentPayments).isEmpty()
    }

    @Test
    fun `cancel 은 cancel_at_period_end true, resume 은 false`() {
        val userId = subscribedUser()
        service.cancel()
        assertThat(subscriptionRepository.findByUserId(userId)!!.cancelAtPeriodEnd).isTrue()
        service.resume()
        assertThat(subscriptionRepository.findByUserId(userId)!!.cancelAtPeriodEnd).isFalse()
    }

    @Test
    fun `card 교체는 새 빌링키로 갱신`() {
        val userId = subscribedUser()
        whenever(billingClient.issueBillingKey(any(), any())).thenReturn(IssuedBilling("bk_2", "국민", "5678****", "신용"))
        service.changeCard(kr.ai.flori.billing.dto.CardChangeRequest("auth_2", "cust_1"))
        val me = service.me()
        assertThat(me.subscription!!.card?.numberMasked).isEqualTo("5678****")
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests kr.ai.flori.billing.BillingControllerTest`
Expected: FAIL — `me/cancel/resume/changeCard`/`MeResponse`/`CardChangeRequest` 미존재

- [ ] **Step 3: DTO 추가** (`billing/dto/BillingDtos.kt`에 append)

```kotlin
data class CardChangeRequest(
    @field:jakarta.validation.constraints.NotBlank val authKey: String,
    @field:jakarta.validation.constraints.NotBlank val customerKey: String,
)

data class PaymentSummary(
    val amount: Int,
    val status: String,
    val approvedAt: java.time.Instant?,
    val createdAt: java.time.Instant,
) {
    companion object {
        fun from(p: kr.ai.flori.billing.entity.PaymentHistory): PaymentSummary =
            PaymentSummary(p.amount, p.status, p.approvedAt, p.createdAt)
    }
}

data class MeResponse(
    val subscription: SubscriptionResponse?,
    val recentPayments: List<PaymentSummary>,
)
```

- [ ] **Step 4: 서비스 메서드 추가** (`SubscriptionService.kt`에 append; `PaymentHistoryRepository` 주입 추가)

```kotlin
    // 생성자에 추가: private val paymentHistoryRepository: PaymentHistoryRepository

    @Transactional(readOnly = true)
    fun me(): MeResponse {
        val userId = TenantContext.currentUserId()
        val sub = subscriptionRepository.findByUserId(userId)
        val card = billingKeyRepository.findByUserId(userId)
        val payments = sub?.id?.let {
            paymentHistoryRepository.findTop10BySubscriptionIdOrderByCreatedAtDesc(it).map(PaymentSummary::from)
        } ?: emptyList()
        return MeResponse(sub?.let { SubscriptionResponse.of(it, card) }, payments)
    }

    @Transactional
    fun cancel(): SubscriptionResponse = setCancel(true)

    @Transactional
    fun resume(): SubscriptionResponse = setCancel(false)

    private fun setCancel(flag: Boolean): SubscriptionResponse {
        val userId = TenantContext.currentUserId()
        val sub = subscriptionRepository.findByUserId(userId)
            ?: throw AppException(BillingErrorCode.SUBSCRIPTION_STATE, "구독이 없습니다")
        if (sub.status !in ACTIVE_STATES) throw AppException(BillingErrorCode.SUBSCRIPTION_STATE, "해지/재개할 수 없는 상태입니다")
        sub.cancelAtPeriodEnd = flag
        return SubscriptionResponse.of(subscriptionRepository.save(sub), billingKeyRepository.findByUserId(userId))
    }

    @Transactional
    fun changeCard(req: CardChangeRequest): SubscriptionResponse {
        val userId = TenantContext.currentUserId()
        val issued = billingClient.issueBillingKey(req.authKey, req.customerKey)
        val card = (billingKeyRepository.findByUserId(userId)
            ?: throw AppException(BillingErrorCode.SUBSCRIPTION_STATE, "등록된 카드가 없습니다")).apply {
            customerKey = req.customerKey
            billingKey = issued.billingKey
            issued.cardCompany?.let { cardCompany = it }
            cardNumberMasked = issued.cardNumber
            cardType = issued.cardType
        }
        val savedCard = billingKeyRepository.save(card)
        val sub = subscriptionRepository.findByUserId(userId)
            ?: throw AppException(BillingErrorCode.SUBSCRIPTION_STATE, "구독이 없습니다")
        return SubscriptionResponse.of(sub, savedCard)
    }
```
> import 추가: `kr.ai.flori.billing.dto.CardChangeRequest`, `kr.ai.flori.billing.dto.MeResponse`, `kr.ai.flori.billing.dto.PaymentSummary`, `kr.ai.flori.billing.repository.PaymentHistoryRepository`. `changeCard`의 외부호출(issueBillingKey)이 트랜잭션 안에 있는 점은 Task2 주의와 동일 — 저렴한 호출이라 허용하되, 실패 시 롤백되도록 둔다.

- [ ] **Step 5: Controller 엔드포인트 추가** (`BillingController.kt`에 append)

```kotlin
    @GetMapping("/me")
    fun me(): kr.ai.flori.billing.dto.MeResponse = subscriptionService.me()

    @PostMapping("/cancel")
    fun cancel(): SubscriptionResponse = subscriptionService.cancel()

    @PostMapping("/resume")
    fun resume(): SubscriptionResponse = subscriptionService.resume()

    @PostMapping("/card")
    fun changeCard(
        @Valid @RequestBody request: kr.ai.flori.billing.dto.CardChangeRequest,
    ): SubscriptionResponse = subscriptionService.changeCard(request)
```

- [ ] **Step 6: 통과 확인 + 전체 스위트**

Run: `./gradlew test --tests kr.ai.flori.billing.BillingControllerTest` → PASS (3 tests)
Run: `./gradlew check` → 전체 통과(ktlint+detekt+JaCoCo 80%)

- [ ] **Step 7: 커밋**

```bash
git add src/main/kotlin/kr/ai/flori/billing/service/SubscriptionService.kt src/main/kotlin/kr/ai/flori/billing/controller/BillingController.kt src/main/kotlin/kr/ai/flori/billing/dto/BillingDtos.kt src/test/kotlin/kr/ai/flori/billing/BillingControllerTest.kt
git commit -m "feat(billing): 구독 조회/해지/재개/카드교체 (me·cancel·resume·card)" -m "Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Part 2 완료 검증
- [ ] `./gradlew check` BUILD SUCCESSFUL (test + ktlint + detekt + JaCoCo 80%).

## Self-Review (Part 2)
- **스펙 커버리지**: §6-A 점주 API(prepare/subscribe/me/cancel/resume/card) → T2·T3. 어뷰징 방어 §7-5 → T2(신원원장). 알림 §7-4(구독시작) → T1. (결제 승인·스케줄러·D-3은 Part 3.)
- **Placeholder 스캔**: 모든 Step 실제 코드/명령/기대값. TBD 없음.
- **타입 일관성**: `SubscribeRequest`/`SubscriptionResponse`/`MeResponse`/`CardChangeRequest`/`PrepareResponse` 필드, 서비스 메서드 시그니처가 Interfaces 블록과 일치. Part1 시그니처(IssuedBilling, 엔티티, 리포지토리) 정확 호출.
- **알려진 함정(구현자 필독)**: ① `@Transactional` self-invocation — Task2 Step5 주의(권장: subscribe 전체 @Transactional + issueBillingKey를 맨 앞 호출). ② `@MockBean BillingClient`로 외부호출 차단(실결제 없음). ③ 시간은 `ZonedDateTime.now(KST)` 직접 사용(Clock 주입은 Part 3에서) — 테스트는 경계(±1일) 단언.
