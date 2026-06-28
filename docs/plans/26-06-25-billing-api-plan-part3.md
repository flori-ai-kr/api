# 토스 빌링 API 구현 계획 — Part 3: 결제 엔진

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax.

**Goal:** 정기 자동결제를 굴리는 엔진 — 매일 도는 스케줄러가 체험 만료 첫 결제·갱신·연체(dunning) 재시도·만료를 처리하고, 결제 3일 전 사전 알림을 보낸다. 외부 토스 호출엔 타임아웃을 적용한다.

**Architecture:** `RecurringBillingScheduler`(@Scheduled KST 04:00, 기존 `RecurringExpenseGenerator` 패턴: 건별 격리·멱등) → `PaymentService.chargeOnce`(재사용 과금 단위: 멱등 체크 → 토스 승인 → payment_history 기록 → 성공 시 주기 전진). 실패 시 dunning 상태전이는 스케줄러가 담당. 결제 성공/실패/만료는 도메인 이벤트로 디스코드+푸시.

**Tech Stack:** 동일 + `@Scheduled`/`@EnableScheduling`(기존 `ScheduleConfig`), `SimpleClientHttpRequestFactory` 타임아웃(기존 `AiServerClient` 패턴).

## Global Constraints

- 스케줄러 cron `0 0 4 * * *` zone `Asia/Seoul`. 시각 `Instant`, 날짜 `LocalDate.now(KST)`.
- dunning: 결제 실패 → `IN_GRACE`, `grace_until = 오늘+3일`, 매일 재시도(next_billing_at 고정), `오늘 >= grace_until`이면 `EXPIRED`.
- `cancel_at_period_end=true`인 due 구독 → 결제 없이 `EXPIRED`.
- **멱등(이중청구 방지)**: 한 주기당 PAID 1건 — `existsBySubscriptionIdAndBillingCycleAndStatus(subId, cycle, "PAID")` 선검사 + 토스 `Idempotency-Key`(=orderId) + `payment_history` 부분유니크(Part1). `billing_cycle = LocalDate.ofInstant(sub.nextBillingAt, KST)`(재시도 시 고정 → 같은 주기).
- `orderId = "sub{subId}_{cycle:yyyyMMdd}_a{attempt}"` (attempt = 해당 주기 기존 payment_history 수 + 1; 시도마다 유니크).
- 주기 전진: MONTHLY → +1개월, YEARLY → +1년 (`ZonedDateTime(KST)`).
- 외부 승인 호출은 건별 `@Transactional` 안(저렴, 실패 시 FAILED 기록은 보존돼야 하므로 chargeOnce가 예외를 삼키고 결과 반환).
- 동시성: 단일 인스턴스 가정(기존 스케줄러 동일). 멱등으로 방어.
- **[Part1 이월 제약]** PaymentHistory subscriptionId 메서드는 스케줄러가 넘기는 sub.id(전체 테넌트 배치이므로 정당 — 화이트리스트 등록됨).
- **[Part2 이월]** 결제 실패 사유 코드 정밀화는 추후(Part3는 실패=재시도 일괄). BillingClient 타임아웃은 T1에서 적용.
- ktlint/detekt 준수(세미콜론·단일줄 인자 금지, 선언 사이 빈 줄), JaCoCo 80%. **태스크마다 커밋 전 `./gradlew check` + `git status` 클린 확인.** Git: 변경 파일만 `git add`(`-A` 금지), `Co-Authored-By: Claude <noreply@anthropic.com>`.

## Part 1/2 인터페이스 (이 계획이 호출)
- `BillingClient.approveBilling(billingKey, customerKey, amount, orderId, orderName, idempotencyKey): ApprovedPayment(paymentKey, orderId, approvedAt)` — 거절 시 `AppException(PAYMENT_REJECTED)` throw.
- `Subscription`(var: status, amount, nextBillingAt, currentPeriodStart, currentPeriodEnd, cancelAtPeriodEnd, graceUntil, retryCount, billingKeyId, plan)
- `BillingKey`(var: billingKey(복호화됨), customerKey) — `BillingKeyRepository.findByUserId(userId)`
- `PaymentHistory(userId, subscriptionId, orderId, billingCycle: LocalDate, amount, status)` + var tossPaymentKey/failureCode/failureMessage/approvedAt
- `SubscriptionRepository.findByStatusInAndNextBillingAtLessThanEqual(statuses, at)`, `findByStatusInAndNextBillingAtBetween(statuses, from, to)`, `findByUserId`, `save`
- `PaymentHistoryRepository`: `existsBySubscriptionIdAndBillingCycleAndStatus`, `findByOrderId`, `save`; 주기별 시도 수 → 아래 T2에서 메서드 추가
- `TossPaymentsProperties(secretKey, baseUrl, connectTimeoutMs, readTimeoutMs)`
- 이벤트: `SubscriptionStartedEvent`(Part2). 신규 이벤트는 T3.
- `DiscordNotifier.notify(DiscordChannel.BILLING, DiscordMessage.of(text))`, `PushDispatcher.sendToUser(userId, title, body)`
- `ApplicationEventPublisher`, `@TransactionalEventListener(AFTER_COMMIT)`

## File Structure (Part 3)
- Modify: `billing/client/BillingClient.kt` (생성자 Builder→RestClient), `billing/config/BillingConfig.kt` (tossRestClient 빈)
- Modify: `billing/client/BillingClientTest.kt` (RestClient 주입으로 갱신)
- Create: `billing/support/BillingPeriods.kt`
- Create: `billing/service/PaymentService.kt`
- Modify: `billing/repository/PaymentHistoryRepository.kt` (주기별 카운트)
- Create: `billing/service/RecurringBillingScheduler.kt`
- Modify: `billing/event/SubscriptionStartedEvent.kt`(또는 신규 파일) — 결제 이벤트, `billing/listener/BillingEventListener.kt`
- Tests: `PaymentServiceTest.kt`, `RecurringBillingSchedulerTest.kt`, `BillingReminderSchedulerTest.kt`

---

## Task 1: BillingClient 타임아웃 적용 (Builder→RestClient 빈)

**Files:**
- Modify: `billing/config/BillingConfig.kt` (`tossRestClient` 빈 추가)
- Modify: `billing/client/BillingClient.kt` (생성자 `RestClient.Builder` → `RestClient`)
- Modify: `billing/client/BillingClientTest.kt` (주입 방식 갱신)

**Interfaces:**
- Produces: `BillingConfig.tossRestClient(properties): RestClient` (connect/read 타임아웃 적용). `BillingClient(properties, restClient)` 시그니처.

- [ ] **Step 1: 테스트를 RestClient 주입으로 변경** (`BillingClientTest.kt`의 `clientWith()` 수정)

기존 `BillingClient(props, builder)` → builder로 RestClient를 만들어 주입:

```kotlin
    private fun clientWith(): Pair<BillingClient, MockRestServiceServer> {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        return BillingClient(props, builder.build()) to server
    }
```
(나머지 3개 테스트 본문은 그대로. import에서 안 쓰는 `RestClient.Builder` 관련 없으면 둠.)

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests kr.ai.flori.billing.client.BillingClientTest`
Expected: FAIL — `BillingClient` 생성자가 아직 `RestClient.Builder`를 받음(컴파일 에러)

- [ ] **Step 3: BillingClient 생성자 변경**

`BillingClient.kt`에서 생성자/필드 수정 (나머지 메서드 동일):

```kotlin
@Component
class BillingClient(
    private val properties: TossPaymentsProperties,
    private val restClient: RestClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val authHeader: String =
        "Basic " + Base64.getEncoder().encodeToString("${properties.secretKey}:".toByteArray())
    // 기존 `private val restClient: RestClient = builder.build()` 줄은 제거(주입으로 대체)
```

- [ ] **Step 4: BillingConfig에 타임아웃 빈 추가** (기존 `AiServerClient` 패턴 = `SimpleClientHttpRequestFactory`)

`BillingConfig.kt`에 추가:

```kotlin
    @Bean
    fun tossRestClient(properties: TossPaymentsProperties): RestClient =
        RestClient
            .builder()
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(properties.connectTimeoutMs)
                    setReadTimeout(properties.readTimeoutMs)
                },
            ).build()
```
import: `org.springframework.context.annotation.Bean`, `org.springframework.http.client.SimpleClientHttpRequestFactory`, `org.springframework.web.client.RestClient`.

- [ ] **Step 5: 통과 + 게이트**

Run: `./gradlew test --tests kr.ai.flori.billing.client.BillingClientTest` → PASS (3)
Run: `./gradlew check` → BUILD SUCCESSFUL. `git status` 클린 확인.

- [ ] **Step 6: 커밋**

```bash
git add src/main/kotlin/kr/ai/flori/billing/config/BillingConfig.kt src/main/kotlin/kr/ai/flori/billing/client/BillingClient.kt src/test/kotlin/kr/ai/flori/billing/client/BillingClientTest.kt
git commit -m "feat(billing): 토스 RestClient 타임아웃 적용(스케줄러 행 방지)" -m "Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 2: PaymentService.chargeOnce (재사용 과금 단위) + BillingPeriods

**Files:**
- Create: `billing/support/BillingPeriods.kt`
- Create: `billing/service/PaymentService.kt`
- Modify: `billing/repository/PaymentHistoryRepository.kt` (주기별 시도 수)
- Test: `billing/PaymentServiceTest.kt`

**Interfaces:**
- Produces:
  - `BillingPeriods.next(plan: String, from: ZonedDateTime): ZonedDateTime`
  - `PaymentHistoryRepository.countBySubscriptionIdAndBillingCycle(subscriptionId: Long, billingCycle: LocalDate): Long`
  - `enum class ChargeOutcome { SUCCESS, FAILED, ALREADY_PAID }`
  - `PaymentService.chargeOnce(subscription: Subscription): ChargeOutcome` — @Transactional. PAID 기록 + 주기 전진(성공) / FAILED 기록(실패, 예외 삼킴) / 멱등 skip.

- [ ] **Step 1: 실패 테스트** (`PaymentServiceTest.kt`)

```kotlin
package kr.ai.flori.billing

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.billing.client.ApprovedPayment
import kr.ai.flori.billing.client.BillingClient
import kr.ai.flori.billing.entity.BillingKey
import kr.ai.flori.billing.entity.Subscription
import kr.ai.flori.billing.repository.BillingKeyRepository
import kr.ai.flori.billing.repository.PaymentHistoryRepository
import kr.ai.flori.billing.repository.SubscriptionRepository
import kr.ai.flori.billing.service.ChargeOutcome
import kr.ai.flori.billing.service.PaymentService
import kr.ai.flori.billing.error.BillingErrorCode
import kr.ai.flori.common.error.AppException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class PaymentServiceTest {
    @Autowired lateinit var paymentService: PaymentService
    @Autowired lateinit var subscriptionRepository: SubscriptionRepository
    @Autowired lateinit var billingKeyRepository: BillingKeyRepository
    @Autowired lateinit var paymentHistoryRepository: PaymentHistoryRepository

    @MockitoBean lateinit var billingClient: BillingClient

    private val kst = ZoneId.of("Asia/Seoul")

    private fun seed(userId: Long): Subscription {
        billingKeyRepository.save(BillingKey(userId, "cust_$userId", "bk_$userId"))
        val now = Instant.now()
        return subscriptionRepository.save(
            Subscription(
                userId = userId,
                plan = "MONTHLY",
                status = "TRIALING",
                amount = 14900,
                nextBillingAt = now,
            ).apply {
                currentPeriodStart = now
                currentPeriodEnd = now
            },
        )
    }

    @Test
    fun `성공시 PAID 기록 + ACTIVE + 다음달 주기 전진`() {
        val sub = seed(101L)
        Mockito
            .`when`(billingClient.approveBilling(anyString(), anyString(), anyInt(), anyString(), anyString(), anyString()))
            .thenReturn(ApprovedPayment("pay_1", "ord_1", "2026-07-08T04:00:00+09:00"))

        val outcome = paymentService.chargeOnce(sub)

        assertThat(outcome).isEqualTo(ChargeOutcome.SUCCESS)
        val updated = subscriptionRepository.findById(sub.id!!).get()
        assertThat(updated.status).isEqualTo("ACTIVE")
        assertThat(updated.nextBillingAt).isBetween(
            Instant.now().plus(27, ChronoUnit.DAYS),
            Instant.now().plus(32, ChronoUnit.DAYS),
        )
        assertThat(paymentHistoryRepository.findAll().count { it.status == "PAID" }).isEqualTo(1)
    }

    @Test
    fun `실패시 FAILED 기록 + 구독 상태 불변 + 예외 안던짐`() {
        val sub = seed(102L)
        Mockito
            .`when`(billingClient.approveBilling(anyString(), anyString(), anyInt(), anyString(), anyString(), anyString()))
            .thenThrow(AppException(BillingErrorCode.PAYMENT_REJECTED))

        val outcome = paymentService.chargeOnce(sub)

        assertThat(outcome).isEqualTo(ChargeOutcome.FAILED)
        val updated = subscriptionRepository.findById(sub.id!!).get()
        assertThat(updated.status).isEqualTo("TRIALING") // 상태 불변(dunning은 스케줄러)
        assertThat(paymentHistoryRepository.findAll().count { it.status == "FAILED" }).isEqualTo(1)
    }

    @Test
    fun `이미 PAID 주기면 멱등 skip`() {
        val sub = seed(103L)
        Mockito
            .`when`(billingClient.approveBilling(anyString(), anyString(), anyInt(), anyString(), anyString(), anyString()))
            .thenReturn(ApprovedPayment("pay_1", "ord_1", null))
        paymentService.chargeOnce(sub)
        // 같은 주기 재호출(next_billing_at 동일) → skip
        val again = subscriptionRepository.findById(sub.id!!).get()
        again.nextBillingAt = sub.nextBillingAt // 같은 주기로 강제
        subscriptionRepository.save(again)

        val outcome = paymentService.chargeOnce(again)
        assertThat(outcome).isEqualTo(ChargeOutcome.ALREADY_PAID)
        assertThat(paymentHistoryRepository.findAll().count { it.status == "PAID" }).isEqualTo(1)
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests kr.ai.flori.billing.PaymentServiceTest`
Expected: FAIL — `PaymentService`/`ChargeOutcome`/`BillingPeriods`/`countBy...` 미존재

- [ ] **Step 3: BillingPeriods** (`billing/support/BillingPeriods.kt`)

```kotlin
package kr.ai.flori.billing.support

import java.time.ZonedDateTime

/** 플랜별 다음 주기 계산. MONTHLY=+1개월, YEARLY=+1년. */
object BillingPeriods {
    fun next(
        plan: String,
        from: ZonedDateTime,
    ): ZonedDateTime =
        when (plan) {
            "YEARLY" -> from.plusYears(1)
            else -> from.plusMonths(1)
        }
}
```

- [ ] **Step 4: 리포지토리 메서드 추가** (`PaymentHistoryRepository.kt`)

```kotlin
    fun countBySubscriptionIdAndBillingCycle(
        subscriptionId: Long,
        billingCycle: LocalDate,
    ): Long
```

- [ ] **Step 5: PaymentService** (`billing/service/PaymentService.kt`)

```kotlin
package kr.ai.flori.billing.service

import kr.ai.flori.billing.client.BillingClient
import kr.ai.flori.billing.entity.PaymentHistory
import kr.ai.flori.billing.entity.Subscription
import kr.ai.flori.billing.repository.BillingKeyRepository
import kr.ai.flori.billing.repository.PaymentHistoryRepository
import kr.ai.flori.billing.repository.SubscriptionRepository
import kr.ai.flori.billing.support.BillingPeriods
import kr.ai.flori.common.error.AppException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class ChargeOutcome { SUCCESS, FAILED, ALREADY_PAID }

/** 단일 결제 단위(스케줄러가 호출). 멱등·기록·성공 시 주기 전진. dunning 상태전이는 스케줄러 책임. */
@Service
class PaymentService(
    private val billingClient: BillingClient,
    private val billingKeyRepository: BillingKeyRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val paymentHistoryRepository: PaymentHistoryRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun chargeOnce(subscription: Subscription): ChargeOutcome {
        val subId = requireNotNull(subscription.id)
        val cycle = LocalDate.ofInstant(subscription.nextBillingAt, KST)
        if (paymentHistoryRepository.existsBySubscriptionIdAndBillingCycleAndStatus(subId, cycle, "PAID")) {
            return ChargeOutcome.ALREADY_PAID
        }
        val card =
            billingKeyRepository.findByUserId(subscription.userId)
                ?: throw AppException(kr.ai.flori.billing.error.BillingErrorCode.SUBSCRIPTION_STATE, "등록된 카드가 없습니다")
        val attempt = paymentHistoryRepository.countBySubscriptionIdAndBillingCycle(subId, cycle) + 1
        val orderId = "sub${subId}_${cycle.format(YYYYMMDD)}_a$attempt"

        return try {
            val approved =
                billingClient.approveBilling(
                    billingKey = card.billingKey,
                    customerKey = card.customerKey,
                    amount = subscription.amount,
                    orderId = orderId,
                    orderName = orderName(subscription.plan),
                    idempotencyKey = orderId,
                )
            paymentHistoryRepository.save(
                PaymentHistory(subscription.userId, subId, orderId, cycle, subscription.amount, "PAID").apply {
                    tossPaymentKey = approved.paymentKey
                    approvedAt = parseApprovedAt(approved.approvedAt)
                },
            )
            advance(subscription)
            ChargeOutcome.SUCCESS
        } catch (e: AppException) {
            log.warn("자동결제 실패 subId={} cycle={} code={}", subId, cycle, e.errorCode.code)
            paymentHistoryRepository.save(
                PaymentHistory(subscription.userId, subId, orderId, cycle, subscription.amount, "FAILED").apply {
                    failureCode = e.errorCode.code
                    failureMessage = e.message
                },
            )
            ChargeOutcome.FAILED
        }
    }

    private fun advance(subscription: Subscription) {
        val startZdt = subscription.nextBillingAt.atZone(KST)
        val nextZdt = BillingPeriods.next(subscription.plan, startZdt)
        subscription.currentPeriodStart = subscription.nextBillingAt
        subscription.currentPeriodEnd = nextZdt.toInstant()
        subscription.nextBillingAt = nextZdt.toInstant()
        subscription.status = "ACTIVE"
        subscription.retryCount = 0
        subscription.graceUntil = null
        subscriptionRepository.save(subscription)
    }

    private fun parseApprovedAt(value: String?): Instant? =
        value?.let {
            runCatching { java.time.OffsetDateTime.parse(it).toInstant() }.getOrNull()
        }

    private fun orderName(plan: String): String = if (plan == "YEARLY") "Flori 연간 구독" else "Flori 월간 구독"

    private companion object {
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
        val YYYYMMDD: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    }
}
```

- [ ] **Step 6: 통과 + 게이트**

Run: `./gradlew test --tests kr.ai.flori.billing.PaymentServiceTest` → PASS (3)
Run: `./gradlew check` → SUCCESSFUL. `git status` 클린.

- [ ] **Step 7: 커밋**

```bash
git add src/main/kotlin/kr/ai/flori/billing/support/BillingPeriods.kt src/main/kotlin/kr/ai/flori/billing/service/PaymentService.kt src/main/kotlin/kr/ai/flori/billing/repository/PaymentHistoryRepository.kt src/test/kotlin/kr/ai/flori/billing/PaymentServiceTest.kt
git commit -m "feat(billing): PaymentService.chargeOnce — 멱등 단일결제 단위" -m "Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 3: RecurringBillingScheduler (엔진: 갱신·dunning·만료) + 결제 이벤트

**Files:**
- Create: `billing/service/RecurringBillingScheduler.kt`
- Modify: `billing/event/SubscriptionStartedEvent.kt` 위치에 결제 이벤트 추가 → **신규 파일** `billing/event/PaymentChargedEvent.kt`, `billing/event/SubscriptionExpiredEvent.kt`(단일 클래스=파일명 일치, detekt 회피)
- Modify: `billing/listener/BillingEventListener.kt` (핸들러 추가)
- Test: `billing/RecurringBillingSchedulerTest.kt`

**Interfaces:**
- Consumes: `PaymentService.chargeOnce`, `SubscriptionRepository.findByStatusInAndNextBillingAtLessThanEqual`.
- Produces: `RecurringBillingScheduler.runDueCharges(now: Instant)` (테스트 주입용 공개 메서드) + `@Scheduled scheduledRun()`. 이벤트 `PaymentChargedEvent(userId, subscriptionId, amount, success: Boolean)`, `SubscriptionExpiredEvent(userId, subscriptionId, reason: String)`.

- [ ] **Step 1: 실패 테스트** (`RecurringBillingSchedulerTest.kt`)

```kotlin
package kr.ai.flori.billing

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.billing.client.ApprovedPayment
import kr.ai.flori.billing.client.BillingClient
import kr.ai.flori.billing.entity.BillingKey
import kr.ai.flori.billing.entity.Subscription
import kr.ai.flori.billing.error.BillingErrorCode
import kr.ai.flori.billing.repository.BillingKeyRepository
import kr.ai.flori.billing.repository.SubscriptionRepository
import kr.ai.flori.billing.service.RecurringBillingScheduler
import kr.ai.flori.common.error.AppException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Instant
import java.time.temporal.ChronoUnit

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class RecurringBillingSchedulerTest {
    @Autowired lateinit var scheduler: RecurringBillingScheduler
    @Autowired lateinit var subscriptionRepository: SubscriptionRepository
    @Autowired lateinit var billingKeyRepository: BillingKeyRepository

    @MockitoBean lateinit var billingClient: BillingClient

    private fun due(userId: Long, status: String, cancel: Boolean = false, graceDaysAgo: Long? = null): Subscription {
        billingKeyRepository.save(BillingKey(userId, "cust_$userId", "bk_$userId"))
        val past = Instant.now().minus(1, ChronoUnit.HOURS)
        return subscriptionRepository.save(
            Subscription(userId, "MONTHLY", status, 14900, past).apply {
                currentPeriodStart = past
                currentPeriodEnd = past
                cancelAtPeriodEnd = cancel
                graceUntil = graceDaysAgo?.let { Instant.now().minus(it, ChronoUnit.DAYS) }
            },
        )
    }

    @Test
    fun `due 구독 결제 성공시 ACTIVE 전진`() {
        val sub = due(201L, "TRIALING")
        Mockito.`when`(billingClient.approveBilling(anyString(), anyString(), anyInt(), anyString(), anyString(), anyString()))
            .thenReturn(ApprovedPayment("pay", "ord", null))
        scheduler.runDueCharges(Instant.now())
        assertThat(subscriptionRepository.findById(sub.id!!).get().status).isEqualTo("ACTIVE")
    }

    @Test
    fun `결제 실패시 IN_GRACE + grace_until 3일 설정`() {
        val sub = due(202L, "ACTIVE")
        Mockito.`when`(billingClient.approveBilling(anyString(), anyString(), anyInt(), anyString(), anyString(), anyString()))
            .thenThrow(AppException(BillingErrorCode.PAYMENT_REJECTED))
        scheduler.runDueCharges(Instant.now())
        val updated = subscriptionRepository.findById(sub.id!!).get()
        assertThat(updated.status).isEqualTo("IN_GRACE")
        assertThat(updated.graceUntil).isNotNull
    }

    @Test
    fun `유예 지난 IN_GRACE 결제 실패시 EXPIRED`() {
        val sub = due(203L, "IN_GRACE", graceDaysAgo = 1L) // grace_until = 어제(이미 경과)
        Mockito.`when`(billingClient.approveBilling(anyString(), anyString(), anyInt(), anyString(), anyString(), anyString()))
            .thenThrow(AppException(BillingErrorCode.PAYMENT_REJECTED))
        scheduler.runDueCharges(Instant.now())
        assertThat(subscriptionRepository.findById(sub.id!!).get().status).isEqualTo("EXPIRED")
    }

    @Test
    fun `해지예약 due 구독은 결제없이 EXPIRED`() {
        val sub = due(204L, "ACTIVE", cancel = true)
        scheduler.runDueCharges(Instant.now())
        assertThat(subscriptionRepository.findById(sub.id!!).get().status).isEqualTo("EXPIRED")
        Mockito.verifyNoInteractions(billingClient)
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests kr.ai.flori.billing.RecurringBillingSchedulerTest`
Expected: FAIL — `RecurringBillingScheduler` 미존재

- [ ] **Step 3: 이벤트 2종(각 파일=클래스명)**

`billing/event/PaymentChargedEvent.kt`:
```kotlin
package kr.ai.flori.billing.event

data class PaymentChargedEvent(
    val userId: Long,
    val subscriptionId: Long,
    val amount: Int,
    val success: Boolean,
)
```
`billing/event/SubscriptionExpiredEvent.kt`:
```kotlin
package kr.ai.flori.billing.event

data class SubscriptionExpiredEvent(
    val userId: Long,
    val subscriptionId: Long,
    val reason: String,
)
```

- [ ] **Step 4: 스케줄러** (`billing/service/RecurringBillingScheduler.kt`)

```kotlin
package kr.ai.flori.billing.service

import kr.ai.flori.billing.entity.Subscription
import kr.ai.flori.billing.event.PaymentChargedEvent
import kr.ai.flori.billing.event.SubscriptionExpiredEvent
import kr.ai.flori.billing.repository.SubscriptionRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 매일 04:00(KST) 결제일 도래 구독 처리. 건별 격리(한 건 실패가 나머지 막지 않음). dunning 정책 담당. */
@Service
class RecurringBillingScheduler(
    private val subscriptionRepository: SubscriptionRepository,
    private val paymentService: PaymentService,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    fun scheduledRun() {
        val count = runDueCharges(Instant.now())
        log.info("정기결제 처리 완료: processed={}", count)
    }

    fun runDueCharges(now: Instant): Int {
        val due = subscriptionRepository.findByStatusInAndNextBillingAtLessThanEqual(DUE_STATES, now)
        var processed = 0
        due.forEach { sub ->
            runCatching { processOne(sub, now) }
                .onFailure { log.error("정기결제 건 처리 실패 subId={}", sub.id, it) }
            processed++
        }
        return processed
    }

    @Transactional
    fun processOne(subscription: Subscription, now: Instant) {
        val subId = requireNotNull(subscription.id)
        if (subscription.cancelAtPeriodEnd) {
            expire(subscription, "해지 예약")
            return
        }
        when (paymentService.chargeOnce(subscription)) {
            ChargeOutcome.SUCCESS, ChargeOutcome.ALREADY_PAID -> {
                eventPublisher.publishEvent(PaymentChargedEvent(subscription.userId, subId, subscription.amount, success = true))
            }
            ChargeOutcome.FAILED -> applyDunning(subscription, now)
        }
    }

    private fun applyDunning(subscription: Subscription, now: Instant) {
        val subId = requireNotNull(subscription.id)
        val fresh = subscriptionRepository.findById(subId).orElse(subscription)
        if (fresh.status != "IN_GRACE") {
            fresh.status = "IN_GRACE"
            fresh.graceUntil = LocalDate.now(KST).plusDays(GRACE_DAYS).atStartOfDay(KST).toInstant()
        }
        fresh.retryCount += 1
        val graceUntil = fresh.graceUntil
        if (graceUntil != null && !now.isBefore(graceUntil)) {
            fresh.status = "EXPIRED"
            subscriptionRepository.save(fresh)
            eventPublisher.publishEvent(SubscriptionExpiredEvent(fresh.userId, subId, "연체 만료"))
            eventPublisher.publishEvent(PaymentChargedEvent(fresh.userId, subId, fresh.amount, success = false))
            return
        }
        subscriptionRepository.save(fresh)
        eventPublisher.publishEvent(PaymentChargedEvent(fresh.userId, subId, fresh.amount, success = false))
    }

    private fun expire(subscription: Subscription, reason: String) {
        subscription.status = "EXPIRED"
        subscriptionRepository.save(subscription)
        eventPublisher.publishEvent(SubscriptionExpiredEvent(subscription.userId, requireNotNull(subscription.id), reason))
    }

    private companion object {
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
        val DUE_STATES = setOf("TRIALING", "ACTIVE", "IN_GRACE")
        const val GRACE_DAYS = 3L
    }
}
```
> 주의: `processOne`/`applyDunning`은 `@Transactional`이며 `runDueCharges`(루프)는 트랜잭션 없음 — 건별 독립 커밋(기존 RecurringExpenseGenerator 패턴). `processOne`은 같은 빈에서 호출되므로 self-invocation 함정 → **`runDueCharges`가 `processOne`을 호출하는 구조에서 `processOne`의 @Transactional이 무효**. 구현자는 이를 (a) `processOne`을 별도 빈(`BillingChargeProcessor`)으로 분리 주입, 또는 (b) `runCatching` 루프 안에서 각 건을 별도 트랜잭션 빈 메서드로 호출하도록 교정하라. **권장: (a)** — `processOne`/`applyDunning`/`expire`를 `BillingChargeProcessor`(@Service)로 분리하고 스케줄러가 주입받아 호출(건별 트랜잭션 보장). 테스트도 그에 맞춰 스케줄러를 통해 검증.

- [ ] **Step 5: 리스너 핸들러 추가** (`BillingEventListener.kt`)

```kotlin
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onPaymentCharged(event: PaymentChargedEvent) {
        if (event.success) {
            discordNotifier.notify(DiscordChannel.BILLING, DiscordMessage.of("**[결제 성공]** userId=${event.userId} ₩${event.amount}"))
        } else {
            discordNotifier.notify(DiscordChannel.BILLING, DiscordMessage.of("**[결제 실패]** userId=${event.userId} ₩${event.amount}"))
            pushDispatcher.sendToUser(event.userId, "결제 실패", "결제가 실패했어요. 카드를 확인해 주세요.")
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onSubscriptionExpired(event: SubscriptionExpiredEvent) {
        discordNotifier.notify(DiscordChannel.BILLING, DiscordMessage.of("**[구독 만료]** userId=${event.userId} 사유=${event.reason}"))
        pushDispatcher.sendToUser(event.userId, "구독 만료", "구독이 만료됐어요.")
    }
```
import: `kr.ai.flori.billing.event.PaymentChargedEvent`, `kr.ai.flori.billing.event.SubscriptionExpiredEvent`.

- [ ] **Step 6: 통과 + 게이트**

Run: `./gradlew test --tests kr.ai.flori.billing.RecurringBillingSchedulerTest` → PASS (4)
Run: `./gradlew check` → SUCCESSFUL. `git status` 클린.

- [ ] **Step 7: 커밋**

```bash
git add src/main/kotlin/kr/ai/flori/billing/service/RecurringBillingScheduler.kt src/main/kotlin/kr/ai/flori/billing/service/BillingChargeProcessor.kt src/main/kotlin/kr/ai/flori/billing/event src/main/kotlin/kr/ai/flori/billing/listener/BillingEventListener.kt src/test/kotlin/kr/ai/flori/billing/RecurringBillingSchedulerTest.kt
git commit -m "feat(billing): 정기결제 스케줄러 + dunning(IN_GRACE 재시도·만료) + 이벤트" -m "Co-Authored-By: Claude <noreply@anthropic.com>"
```
(분리한 `BillingChargeProcessor`가 있으면 함께 add.)

---

## Task 4: D-3 결제 예정 사전 알림 스케줄러

**Files:**
- Create: `billing/service/BillingReminderScheduler.kt`
- Test: `billing/BillingReminderSchedulerTest.kt`

**Interfaces:**
- Consumes: `SubscriptionRepository.findByStatusInAndNextBillingAtBetween`, `PushDispatcher.sendToUser`.
- Produces: `BillingReminderScheduler.runReminders(now: Instant): Int` + `@Scheduled`.

- [ ] **Step 1: 실패 테스트** (`BillingReminderSchedulerTest.kt`)

```kotlin
package kr.ai.flori.billing

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.billing.entity.Subscription
import kr.ai.flori.billing.repository.SubscriptionRepository
import kr.ai.flori.billing.service.BillingReminderScheduler
import kr.ai.flori.common.push.PushDispatcher
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Instant
import java.time.temporal.ChronoUnit

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class BillingReminderSchedulerTest {
    @Autowired lateinit var scheduler: BillingReminderScheduler
    @Autowired lateinit var subscriptionRepository: SubscriptionRepository

    @MockitoBean lateinit var pushDispatcher: PushDispatcher

    @Test
    fun `3일 뒤 결제예정 구독에 푸시 발송`() {
        val now = Instant.now()
        val inThreeDays = now.plus(3, ChronoUnit.DAYS)
        subscriptionRepository.save(
            Subscription(301L, "MONTHLY", "ACTIVE", 14900, inThreeDays),
        )
        Mockito.`when`(pushDispatcher.sendToUser(Mockito.anyLong(), Mockito.anyString(), Mockito.anyString(), Mockito.any()))
            .thenReturn(1)

        val sent = scheduler.runReminders(now)

        org.assertj.core.api.Assertions.assertThat(sent).isEqualTo(1)
        Mockito.verify(pushDispatcher).sendToUser(Mockito.eq(301L), Mockito.anyString(), Mockito.anyString(), Mockito.any())
    }

    @Test
    fun `10일 뒤 결제 구독엔 미발송`() {
        val now = Instant.now()
        subscriptionRepository.save(
            Subscription(302L, "MONTHLY", "ACTIVE", 14900, now.plus(10, ChronoUnit.DAYS)),
        )
        val sent = scheduler.runReminders(now)
        org.assertj.core.api.Assertions.assertThat(sent).isEqualTo(0)
    }
}
```
> `PushDispatcher.sendToUser`는 `url: String? = null` 기본인자가 있어 모킹 시 4-인자 매처를 쓴다(위 테스트). 구현자가 시그니처에 맞춰 매처 조정.

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests kr.ai.flori.billing.BillingReminderSchedulerTest`
Expected: FAIL — `BillingReminderScheduler` 미존재

- [ ] **Step 3: 스케줄러** (`billing/service/BillingReminderScheduler.kt`)

```kotlin
package kr.ai.flori.billing.service

import kr.ai.flori.billing.repository.SubscriptionRepository
import kr.ai.flori.common.push.PushDispatcher
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

/** 매일 04:30(KST) 결제 3일 전 구독에 사전 알림. 윈도=해당 일자 0~24시(중복발송 방지: 하루 1회 실행이므로 자연 1회). */
@Service
class BillingReminderScheduler(
    private val subscriptionRepository: SubscriptionRepository,
    private val pushDispatcher: PushDispatcher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    fun scheduledRun() {
        val sent = runReminders(Instant.now())
        log.info("결제 사전알림 발송: {}", sent)
    }

    fun runReminders(now: Instant): Int {
        val from = now.plus(3, ChronoUnit.DAYS)
        val to = now.plus(4, ChronoUnit.DAYS)
        val targets = subscriptionRepository.findByStatusInAndNextBillingAtBetween(REMIND_STATES, from, to)
        var sent = 0
        targets.forEach { sub ->
            sent += pushDispatcher.sendToUser(sub.userId, "결제 예정 안내", "3일 후 구독료가 결제될 예정이에요.")
        }
        return sent
    }

    private companion object {
        val REMIND_STATES = setOf("TRIALING", "ACTIVE")
    }
}
```
> 윈도 `[now+3d, now+4d)`는 하루 1회 실행 기준 D-3 대상을 1회만 잡는다(중복발송 방지).

- [ ] **Step 4: 통과 + 게이트**

Run: `./gradlew test --tests kr.ai.flori.billing.BillingReminderSchedulerTest` → PASS (2)
Run: `./gradlew check` → SUCCESSFUL. `git status` 클린.

- [ ] **Step 5: 커밋**

```bash
git add src/main/kotlin/kr/ai/flori/billing/service/BillingReminderScheduler.kt src/test/kotlin/kr/ai/flori/billing/BillingReminderSchedulerTest.kt
git commit -m "feat(billing): D-3 결제 예정 사전 알림 스케줄러" -m "Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Part 3 완료 검증
- [ ] `./gradlew check` BUILD SUCCESSFUL + `git status` 클린.

## Self-Review (Part 3)
- **스펙 커버리지**: §7-1 스케줄러/dunning → T3. §7-2 D-3 → T4. chargeOnce 멱등/주기전진 → T2. 타임아웃(이월) → T1. (웹훅·집계는 Part 5.)
- **Placeholder 스캔**: 전 Step 실제 코드/명령/기대값. TBD 없음.
- **타입 일관성**: `ChargeOutcome`, `chargeOnce(Subscription)`, `runDueCharges(Instant)`, `runReminders(Instant)`, 이벤트 필드가 Interfaces와 일치. Part1/2 시그니처(approveBilling, 엔티티, 리포지토리) 정확 호출.
- **알려진 함정(구현자 필독)**: ① T3 `@Transactional` self-invocation → `processOne` 등을 별도 빈 `BillingChargeProcessor`로 분리(권장). ② 모킹은 기존 패턴 `@MockitoBean` + plain Mockito(또는 mockito-kotlin 존재 시 그것). ③ 코드 ktlint 준수(세미콜론 금지·인자 줄바꿈). ④ 시간은 `runDueCharges(now)`/`runReminders(now)`로 주입받아 테스트 결정성 확보(@Scheduled는 now() 위임).
