# 토스 빌링 API 구현 계획 — Part 5: 웹훅 · 구독 집계 (api 백엔드 마무리)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax.

**Goal:** ① 슈퍼어드민 콘솔이 구독 현황을 보도록 `AdminOverview`에 구독 집계 + `GET /admin/subscriptions` 목록을 추가하고, ② 토스 결제 웹훅(`POST /webhooks/toss`)으로 대시보드 수동 환불/취소를 수신해 `payment_history`를 동기화하고 디스코드로 통보한다. 이걸로 **api 백엔드 완성**.

**Architecture:** 웹훅은 **트리거**로만 신뢰하고, `BillingClient.getPayment(paymentKey)`로 토스에 권위 상태를 재조회(re-query)해 반영한다(토스가 웹훅 서명을 문서화하지 않으므로 — 위조 방어). 집계는 기존 `AdminStatsService` 패턴, 목록은 기존 `@RequiresAdmin` 어드민 컨트롤러 패턴.

**Tech Stack:** 동일 + 기존 `Paging.pageSize`, `@RequiresAdmin`, `DiscordNotifier`.

## Global Constraints

- 웹훅 `POST /webhooks/toss`는 **공개(permitAll)** — SecurityConfig에 `/webhooks/**` 추가. 본문을 신뢰하지 않고 `paymentKey`로 **재조회**해 검증.
- 웹훅 처리: eventType ∈ {PAYMENT_STATUS_CHANGED, CANCEL_STATUS_CHANGED} 만 처리. `getPayment(paymentKey)` 상태가 CANCELED/PARTIAL_CANCELED면 매칭되는 `payment_history`를 **CANCELED**로 동기화 + 디스코드 알림. 그 외/미매칭이어도 **항상 200**(토스 재시도 폭주 방지).
- payment_history 매칭: webhook의 paymentKey → `findByTossPaymentKey`. (cross-tenant 웹훅 컨텍스트 — TenantIsolationGuard 화이트리스트 등록.)
- 구독 집계: `SubscriptionCounts(active, trialing, inGrace, expired)` — `countByStatus`로. `active`=ACTIVE만(트라이얼은 trialing 별도). AdminOverviewResponse에 `subscriptions` 필드 추가(non-null).
- `/admin/subscriptions`: `@RequiresAdmin`, 선택 `status` 필터 + page/size 페이지네이션(기존 `Paging.pageSize` 패턴), `AdminSubscriptionRow` 목록.
- 멀티테넌시: 어드민/웹훅은 cross-tenant(정당). 점주 API 아님.
- ktlint/detekt(세미콜론·단일줄 인자 금지), JaCoCo 80%. **태스크마다 커밋 전 `./gradlew check` + `git status` 클린.** Git: 변경 파일만 `git add`(`-A` 금지), `Co-Authored-By: Claude <noreply@anthropic.com>`.

## Part 1~4 인터페이스 (이 계획이 호출)
- `Subscription`(status, plan, amount, nextBillingAt, currentPeriodEnd, cancelAtPeriodEnd, userId, id, createdAt)
- `SubscriptionRepository.findByUserId`; 신규: `countByStatus`, `findByStatusOrderByCreatedAtDesc(status, pageable)`, (목록 전체) `findAllByOrderByCreatedAtDesc(pageable)`
- `PaymentHistory`(status, tossPaymentKey, orderId); `PaymentHistoryRepository.findByOrderId`; 신규 `findByTossPaymentKey`
- `BillingClient(properties, restClient)` — 신규 `getPayment(paymentKey): TossPayment(status, orderId)`. 인증 `Basic base64(secretKey:)`, GET.
- `AdminOverviewResponse`(users, sales, verifications, comparison) → `subscriptions` 추가. `AdminStatsService.overview` 구조.
- `@RequiresAdmin`(kr.ai.flori.admin.gating.RequiresAdmin), `Paging.pageSize(page,size,max)`
- `DiscordNotifier.notify(DiscordChannel.BILLING, DiscordMessage.of(text))`
- `BillingErrorCode`(필요 시 추가), `TenantIsolationGuardTest` 화이트리스트

## File Structure (Part 5)
- Modify: `admin/dto/AdminStatsDtos.kt`(SubscriptionCounts), `admin/service/AdminStatsService.kt`(집계)
- Modify: `billing/repository/SubscriptionRepository.kt`(countByStatus, 목록), `billing/repository/PaymentHistoryRepository.kt`(findByTossPaymentKey)
- Create: `billing/service/AdminSubscriptionService.kt`, `billing/controller/AdminSubscriptionController.kt`, `billing/dto/AdminSubscriptionDtos.kt`
- Modify: `common/security/SecurityConfig.kt`(/webhooks/** permitAll), `billing/client/BillingClient.kt`(getPayment)
- Create: `billing/controller/TossWebhookController.kt`, `billing/service/TossWebhookService.kt`, `billing/dto/TossWebhookDtos.kt`
- Modify: `TenantIsolationGuardTest.kt`(findByTossPaymentKey 화이트리스트)
- Tests: `AdminSubscriptionTest.kt`, `TossWebhookTest.kt`

## 마일스톤
Part1~4 ✅ → **Part 5(이 문서) = api 백엔드 마무리.** 이후 web.

---

## Task 1: 구독 집계(AdminOverview) + /admin/subscriptions 목록

**Files:**
- Modify: `admin/dto/AdminStatsDtos.kt`, `admin/service/AdminStatsService.kt`
- Modify: `billing/repository/SubscriptionRepository.kt`
- Create: `billing/dto/AdminSubscriptionDtos.kt`, `billing/service/AdminSubscriptionService.kt`, `billing/controller/AdminSubscriptionController.kt`
- Test: `billing/AdminSubscriptionTest.kt`

**Interfaces:**
- Produces:
  - `SubscriptionCounts(active, trialing, inGrace, expired)` (Long)
  - `SubscriptionRepository.countByStatus(status: String): Long`, `findByStatusOrderByCreatedAtDesc(status, pageable)`, `findAllByOrderByCreatedAtDesc(pageable)`
  - `AdminSubscriptionService.counts(): SubscriptionCounts`, `list(status: String?, page: Int, size: Int): List<AdminSubscriptionRow>`
  - `AdminSubscriptionRow(userId, plan, status, nextBillingAt, currentPeriodEnd, cancelAtPeriodEnd, createdAt)`
  - `GET /admin/subscriptions?status=&page=&size=`

- [ ] **Step 1: 실패 테스트** (`AdminSubscriptionTest.kt`)

```kotlin
package kr.ai.flori.billing

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.billing.entity.Subscription
import kr.ai.flori.billing.repository.SubscriptionRepository
import kr.ai.flori.billing.service.AdminSubscriptionService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class AdminSubscriptionTest {
    @Autowired lateinit var service: AdminSubscriptionService
    @Autowired lateinit var subscriptionRepository: SubscriptionRepository

    @AfterEach fun cleanup() = subscriptionRepository.deleteAll()

    private fun sub(userId: Long, status: String) {
        subscriptionRepository.save(Subscription(userId, "MONTHLY", status, 14900, Instant.now()))
    }

    @Test
    fun `counts 는 상태별 집계`() {
        sub(1L, "ACTIVE")
        sub(2L, "ACTIVE")
        sub(3L, "TRIALING")
        sub(4L, "IN_GRACE")
        sub(5L, "EXPIRED")
        val c = service.counts()
        assertThat(c.active).isEqualTo(2)
        assertThat(c.trialing).isEqualTo(1)
        assertThat(c.inGrace).isEqualTo(1)
        assertThat(c.expired).isEqualTo(1)
    }

    @Test
    fun `list 는 status 필터`() {
        sub(1L, "ACTIVE")
        sub(2L, "EXPIRED")
        assertThat(service.list("ACTIVE", 0, 50)).hasSize(1)
        assertThat(service.list(null, 0, 50)).hasSize(2)
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests kr.ai.flori.billing.AdminSubscriptionTest`
Expected: FAIL — `AdminSubscriptionService`/`SubscriptionCounts` 미존재(컴파일 에러)

- [ ] **Step 3: 리포지토리 메서드 추가** (`SubscriptionRepository.kt`)

```kotlin
    fun countByStatus(status: String): Long

    fun findByStatusOrderByCreatedAtDesc(
        status: String,
        pageable: org.springframework.data.domain.Pageable,
    ): List<Subscription>

    fun findAllByOrderByCreatedAtDesc(pageable: org.springframework.data.domain.Pageable): List<Subscription>
```

- [ ] **Step 4: SubscriptionCounts DTO** (`admin/dto/AdminStatsDtos.kt`에 append)

```kotlin
data class SubscriptionCounts(
    val active: Long,
    val trialing: Long,
    val inGrace: Long,
    val expired: Long,
)
```

그리고 `AdminOverviewResponse`에 필드 추가:

```kotlin
data class AdminOverviewResponse(
    val users: UserCounts,
    val sales: SalesCounts,
    val verifications: VerificationCounts,
    val subscriptions: SubscriptionCounts,
    val comparison: OverviewComparison?,
)
```

- [ ] **Step 5: AdminSubscription DTO** (`billing/dto/AdminSubscriptionDtos.kt`)

```kotlin
package kr.ai.flori.billing.dto

import kr.ai.flori.billing.entity.Subscription
import java.time.Instant

data class AdminSubscriptionRow(
    val userId: Long,
    val plan: String,
    val status: String,
    val nextBillingAt: Instant,
    val currentPeriodEnd: Instant?,
    val cancelAtPeriodEnd: Boolean,
    val createdAt: Instant,
) {
    companion object {
        fun from(s: Subscription): AdminSubscriptionRow =
            AdminSubscriptionRow(
                userId = s.userId,
                plan = s.plan,
                status = s.status,
                nextBillingAt = s.nextBillingAt,
                currentPeriodEnd = s.currentPeriodEnd,
                cancelAtPeriodEnd = s.cancelAtPeriodEnd,
                createdAt = s.createdAt,
            )
    }
}
```

- [ ] **Step 6: AdminSubscriptionService** (`billing/service/AdminSubscriptionService.kt`)

```kotlin
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
```
> `Paging.pageSize`의 정확한 시그니처/반환형은 기존 사용처(`AdminAuditService.list`)를 확인해 맞출 것(Pageable 반환). import 경로도 그에 맞게.

- [ ] **Step 7: AdminStatsService.overview에 집계 연결** (`admin/service/AdminStatsService.kt`)

`AdminSubscriptionService` 주입 후 overview에 추가:

```kotlin
    // 생성자에 추가: private val adminSubscriptionService: AdminSubscriptionService

    @Transactional(readOnly = true)
    fun overview(range: String): AdminOverviewResponse =
        AdminOverviewResponse(
            users = userCounts(),
            sales = salesCounts(),
            verifications = verificationCounts(),
            subscriptions = adminSubscriptionService.counts(),
            comparison = comparison(range),
        )
```
> 기존 overview 테스트가 `subscriptions` 누락으로 깨지면 그 테스트의 기대 객체에 `subscriptions` 추가(또는 필드 단위 검증이면 무영향). 전체 스위트로 확인.

- [ ] **Step 8: Controller** (`billing/controller/AdminSubscriptionController.kt`)

```kotlin
package kr.ai.flori.billing.controller

import kr.ai.flori.admin.gating.RequiresAdmin
import kr.ai.flori.billing.dto.AdminSubscriptionRow
import kr.ai.flori.billing.service.AdminSubscriptionService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/subscriptions")
@RequiresAdmin
class AdminSubscriptionController(
    private val adminSubscriptionService: AdminSubscriptionService,
) {
    @GetMapping
    fun list(
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): List<AdminSubscriptionRow> = adminSubscriptionService.list(status, page, size)
}
```

- [ ] **Step 9: 통과 + 게이트**

Run: `./gradlew test --tests kr.ai.flori.billing.AdminSubscriptionTest` → PASS (2)
Run: `./gradlew check` → SUCCESSFUL (기존 overview 테스트 포함 전체 통과). `git status` 클린.

- [ ] **Step 10: 커밋**

```bash
git add src/main/kotlin/kr/ai/flori/admin/dto/AdminStatsDtos.kt src/main/kotlin/kr/ai/flori/admin/service/AdminStatsService.kt src/main/kotlin/kr/ai/flori/billing/repository/SubscriptionRepository.kt src/main/kotlin/kr/ai/flori/billing/dto/AdminSubscriptionDtos.kt src/main/kotlin/kr/ai/flori/billing/service/AdminSubscriptionService.kt src/main/kotlin/kr/ai/flori/billing/controller/AdminSubscriptionController.kt src/test/kotlin/kr/ai/flori/billing/AdminSubscriptionTest.kt
git commit -m "feat(billing): AdminOverview 구독집계 + /admin/subscriptions 목록" -m "Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 2: 토스 결제 웹훅 (환불/취소 재조회 동기화)

**Files:**
- Modify: `common/security/SecurityConfig.kt` (/webhooks/** permitAll)
- Modify: `billing/client/BillingClient.kt` (`getPayment`)
- Modify: `billing/repository/PaymentHistoryRepository.kt` (`findByTossPaymentKey`)
- Modify: `TenantIsolationGuardTest.kt` (화이트리스트)
- Create: `billing/dto/TossWebhookDtos.kt`, `billing/service/TossWebhookService.kt`, `billing/controller/TossWebhookController.kt`
- Test: `billing/TossWebhookTest.kt`

**Interfaces:**
- Produces:
  - `BillingClient.getPayment(paymentKey: String): TossPayment(status: String, orderId: String)`
  - `PaymentHistoryRepository.findByTossPaymentKey(tossPaymentKey: String): PaymentHistory?`
  - `TossWebhookService.handle(event: TossWebhookEvent)`
  - `POST /webhooks/toss` (200 always)

- [ ] **Step 1: 실패 테스트** (`TossWebhookTest.kt`)

```kotlin
package kr.ai.flori.billing

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.billing.client.BillingClient
import kr.ai.flori.billing.client.TossPayment
import kr.ai.flori.billing.dto.TossWebhookData
import kr.ai.flori.billing.dto.TossWebhookEvent
import kr.ai.flori.billing.entity.PaymentHistory
import kr.ai.flori.billing.repository.PaymentHistoryRepository
import kr.ai.flori.billing.service.TossWebhookService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.LocalDate

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class TossWebhookTest {
    @Autowired lateinit var service: TossWebhookService
    @Autowired lateinit var paymentHistoryRepository: PaymentHistoryRepository

    @MockitoBean lateinit var billingClient: BillingClient

    @AfterEach fun cleanup() = paymentHistoryRepository.deleteAll()

    private fun paid(orderId: String, paymentKey: String) {
        paymentHistoryRepository.save(
            PaymentHistory(1L, 10L, orderId, LocalDate.of(2026, 7, 8), 14900, "PAID").apply {
                tossPaymentKey = paymentKey
            },
        )
    }

    @Test
    fun `취소 이벤트 재조회 결과 CANCELED면 payment_history CANCELED 동기화`() {
        paid("ord_1", "pay_1")
        Mockito.`when`(billingClient.getPayment("pay_1")).thenReturn(TossPayment("CANCELED", "ord_1"))

        service.handle(TossWebhookEvent("PAYMENT_STATUS_CHANGED", TossWebhookData("pay_1", "ord_1", "CANCELED")))

        assertThat(paymentHistoryRepository.findByOrderId("ord_1")!!.status).isEqualTo("CANCELED")
    }

    @Test
    fun `재조회 결과 여전히 DONE이면 변경 없음`() {
        paid("ord_2", "pay_2")
        Mockito.`when`(billingClient.getPayment("pay_2")).thenReturn(TossPayment("DONE", "ord_2"))

        service.handle(TossWebhookEvent("PAYMENT_STATUS_CHANGED", TossWebhookData("pay_2", "ord_2", "DONE")))

        assertThat(paymentHistoryRepository.findByOrderId("ord_2")!!.status).isEqualTo("PAID")
    }

    @Test
    fun `관련없는 이벤트는 무시(예외 없음)`() {
        service.handle(TossWebhookEvent("METHOD_UPDATED", TossWebhookData(null, null, null)))
        // 예외 없이 통과하면 성공
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests kr.ai.flori.billing.TossWebhookTest`
Expected: FAIL — `TossWebhookService`/`TossPayment`/DTO/`getPayment` 미존재

- [ ] **Step 3: BillingClient.getPayment + TossPayment** (`BillingClient.kt`)

`get` 헬퍼가 없으면 추가(기존 `post` 패턴과 동형, GET):

```kotlin
    fun getPayment(paymentKey: String): TossPayment {
        val res =
            try {
                restClient
                    .get()
                    .uri(properties.baseUrl + "/v1/payments/$paymentKey")
                    .headers { it.set(HttpHeaders.AUTHORIZATION, authHeader) }
                    .retrieve()
                    .body(PaymentResponse::class.java) ?: throw AppException(BillingErrorCode.PAYMENT_REJECTED)
            } catch (e: RestClientResponseException) {
                log.warn("토스 결제조회 실패 paymentKey={} status={}", paymentKey, e.statusCode)
                throw AppException(BillingErrorCode.PAYMENT_REJECTED, cause = e)
            }
        return TossPayment(res.status, res.orderId)
    }
```
파일 하단 응답 data class + 공개 data class 추가:

```kotlin
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PaymentResponse(val status: String, val orderId: String)
```
```kotlin
data class TossPayment(val status: String, val orderId: String)
```
import: `org.springframework.http.HttpHeaders` (이미 있을 수 있음).

- [ ] **Step 4: 리포지토리 + 화이트리스트** (`PaymentHistoryRepository.kt`)

```kotlin
    fun findByTossPaymentKey(tossPaymentKey: String): PaymentHistory?
```
`TenantIsolationGuardTest.kt` 화이트리스트에 사유와 함께 추가(웹훅 콜백 매칭 — cross-tenant 정당, paymentKey는 토스 전역 유일):
```kotlin
        "PaymentHistoryRepository#findByTossPaymentKey", // 웹훅 콜백 매칭(전역 paymentKey)
```

- [ ] **Step 5: 웹훅 DTO** (`billing/dto/TossWebhookDtos.kt`)

```kotlin
package kr.ai.flori.billing.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class TossWebhookEvent(
    val eventType: String? = null,
    val data: TossWebhookData? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TossWebhookData(
    val paymentKey: String? = null,
    val orderId: String? = null,
    val status: String? = null,
)
```

- [ ] **Step 6: WebhookService** (`billing/service/TossWebhookService.kt`)

```kotlin
package kr.ai.flori.billing.service

import kr.ai.flori.billing.client.BillingClient
import kr.ai.flori.billing.dto.TossWebhookEvent
import kr.ai.flori.billing.repository.PaymentHistoryRepository
import kr.ai.flori.common.notification.discord.DiscordChannel
import kr.ai.flori.common.notification.discord.DiscordMessage
import kr.ai.flori.common.notification.discord.DiscordNotifier
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 토스 결제 웹훅 처리. 본문 비신뢰 — paymentKey로 재조회해 권위 상태로 동기화. */
@Service
class TossWebhookService(
    private val billingClient: BillingClient,
    private val paymentHistoryRepository: PaymentHistoryRepository,
    private val discordNotifier: DiscordNotifier,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun handle(event: TossWebhookEvent) {
        if (event.eventType !in HANDLED_EVENTS) return
        val paymentKey = event.data?.paymentKey ?: return
        val payment =
            runCatching { billingClient.getPayment(paymentKey) }
                .getOrElse {
                    log.warn("웹훅 재조회 실패 paymentKey={}", paymentKey)
                    return
                }
        if (payment.status !in CANCELED_STATES) return
        val history = paymentHistoryRepository.findByTossPaymentKey(paymentKey) ?: return
        if (history.status == "CANCELED") return
        history.status = "CANCELED"
        paymentHistoryRepository.save(history)
        discordNotifier.notify(
            DiscordChannel.BILLING,
            DiscordMessage.of("**[환불/취소]** userId=${history.userId} ₩${history.amount} order=${history.orderId}"),
        )
    }

    private companion object {
        val HANDLED_EVENTS = setOf("PAYMENT_STATUS_CHANGED", "CANCEL_STATUS_CHANGED")
        val CANCELED_STATES = setOf("CANCELED", "PARTIAL_CANCELED")
    }
}
```

- [ ] **Step 7: Controller** (`billing/controller/TossWebhookController.kt`)

```kotlin
package kr.ai.flori.billing.controller

import kr.ai.flori.billing.dto.TossWebhookEvent
import kr.ai.flori.billing.service.TossWebhookService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** 토스 결제 웹훅. 공개 엔드포인트(본문 비신뢰, 재조회로 검증). 항상 200. */
@RestController
@RequestMapping("/webhooks/toss")
class TossWebhookController(
    private val tossWebhookService: TossWebhookService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    fun receive(
        @RequestBody event: TossWebhookEvent,
    ) {
        tossWebhookService.handle(event)
    }
}
```

- [ ] **Step 8: SecurityConfig 공개경로 추가** (`SecurityConfig.kt`)

permitAll 목록에 추가(`/interview` 인근):

```kotlin
    authorize("/webhooks/**", permitAll) // 토스 웹훅(본문 비신뢰 — paymentKey 재조회로 검증)
```

- [ ] **Step 9: 통과 + 게이트**

Run: `./gradlew test --tests kr.ai.flori.billing.TossWebhookTest` → PASS (3)
Run: `./gradlew check` → SUCCESSFUL. `git status` 클린.

- [ ] **Step 10: 커밋**

```bash
git add src/main/kotlin/kr/ai/flori/common/security/SecurityConfig.kt src/main/kotlin/kr/ai/flori/billing/client/BillingClient.kt src/main/kotlin/kr/ai/flori/billing/repository/PaymentHistoryRepository.kt src/test/kotlin/kr/ai/flori/admin/AdminTenantIsolation*.kt src/test/kotlin/kr/ai/flori/billing/TossWebhookTest.kt src/main/kotlin/kr/ai/flori/billing/dto/TossWebhookDtos.kt src/main/kotlin/kr/ai/flori/billing/service/TossWebhookService.kt src/main/kotlin/kr/ai/flori/billing/controller/TossWebhookController.kt
# TenantIsolationGuardTest 실제 경로로 add 조정
git commit -m "feat(billing): 토스 결제 웹훅 — 환불/취소 재조회 동기화 + 디스코드" -m "Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Part 5 완료 검증
- [ ] `./gradlew check` BUILD SUCCESSFUL + `git status` 클린 → **api 백엔드 완성**.

## Self-Review (Part 5)
- **스펙 커버리지**: §7-3 웹훅(환불 가시성) → T2(재조회 패턴으로 강화). §5/§8 구독집계·콘솔 → T1. (설계의 "서명검증"은 토스 미문서화로 "재조회"로 대체 — 더 안전.)
- **Placeholder 스캔**: 전 Step 실제 코드/명령/기대값. TBD 없음.
- **타입 일관성**: `SubscriptionCounts`, `AdminSubscriptionRow`, `TossPayment`, `TossWebhookEvent/Data`, `getPayment`/`findByTossPaymentKey`/`countByStatus` 시그니처 일치. Part1~4 시그니처 정확 호출.
- **알려진 함정(구현자 필독)**: ① `Paging.pageSize` 실제 시그니처/반환형 확인 후 맞출 것(기존 AdminAuditService.list 참고). ② AdminOverviewResponse에 필드 추가 시 기존 overview 테스트 기대객체 갱신 필요할 수 있음(전체 스위트로 확인). ③ `findByTossPaymentKey` TenantIsolationGuard 화이트리스트 등록(미등록 시 가드 테스트 실패). ④ 웹훅은 항상 200(예외 던지지 말 것 — service 내부 runCatching/early return). ⑤ 모킹 @MockitoBean+plain Mockito. ⑥ 커밋 전 `./gradlew check` + git status clean.
```
