# 토스 빌링 API 구현 계획 — Part 4: 쿠폰

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax.

**Goal:** 슈퍼어드민이 콘솔에서 쿠폰(무료 일수)을 발행/조회/폐기하고, 점주가 코드를 등록(redeem)하면 무료 일수만큼 다음 결제일이 미뤄진다. 가입 전 등록분은 pending으로 보관됐다가 구독 시작 시 적용(Part 2 연동).

**Architecture:** 점주 redeem(`POST /coupons/redeem`, requireAuth + 레이트리밋) → `CouponService`(검증4종 → CouponRedemption 기록 → redeemed_count 증가 → `SubscriptionService.applyFreeDays`로 날짜 밀기 or pending). 어드민(`/admin/coupons`, `@RequiresAdmin`) → `AdminCouponService`(발행=코드 자동생성+감사로그, 목록/상세=사용현황, 폐기=감사로그). 레이트리밋은 기존 Caffeine 인터셉터 패턴.

**Tech Stack:** 동일 + Caffeine(이미 의존성 — waitlist 사용), `@RequiresAdmin`(AdminInterceptor), `AdminAuditService`.

## Global Constraints

- 쿠폰 검증 4종(redeem 시): ① 존재(findByCode) 아니면 `COUPON_NOT_FOUND` ② `status==ACTIVE` 아니면 `COUPON_DISABLED` ③ now ∈ [valid_from, valid_until] 아니면 `COUPON_NOT_IN_PERIOD` ④ 총량(`redeemed_count < max_redemptions`, max=null이면 무제한) 및 1인한도(`!existsByCouponIdAndUserId`, per_user_limit=1) 아니면 `COUPON_EXHAUSTED`.
- redeem 성공: `CouponRedemption(couponId, userId, grantedDays=coupon.days)` 저장 + `coupon.redeemedCount++` + 날짜 적용.
- 날짜 적용: 구독이 TRIALING/ACTIVE/IN_GRACE면 `next_billing_at += days`(+ current_period_end 동기화). EXPIRED/구독없음이면 **pending**(redemption.subscriptionId=null) — Part 2 `applyPendingCoupons`가 다음 subscribe에서 적용.
- 코드 자동생성: **12자, Crockford Base32(`0123456789ABCDEFGHJKMNPQRSTVWXYZ`, I/L/O/U 제외), `XXXX-XXXX-XXXX`**, `SecureRandom`, `existsByCode` 충돌 시 재생성. 커스텀 직접입력도 허용(공개 캠페인).
- 어드민 API: `@RequiresAdmin` + `/admin/coupons`. 발행/폐기는 `AdminAuditService.record`로 감사로그(`COUPON_ISSUE`/`COUPON_DISABLE`). created_by = `TenantContext.currentUserId()`.
- 콘솔 표시 상태: DB는 ACTIVE/DISABLED. 응답에서 **effective status** 파생: DISABLED → DISABLED, valid_until 지남 → EXPIRED, redeemed_count>=max → EXHAUSTED, 그 외 ACTIVE.
- redeem 레이트리밋: 인증 유저당(userId, 폴백 IP) 윈도 제한. 초과 시 `CommonErrorCode.TOO_MANY_REQUESTS`.
- `/coupons/**`는 SecurityConfig `anyRequest authenticated`로 이미 인증됨(추가 규칙 불필요). `/admin/**`은 인증+AdminInterceptor.
- ktlint/detekt(세미콜론·단일줄 인자 금지), JaCoCo 80%. **태스크마다 커밋 전 `./gradlew check` + `git status` 클린.** Git: 변경 파일만 `git add`(`-A` 금지), `Co-Authored-By: Claude <noreply@anthropic.com>`.
- 동시성: `redeemed_count` 증가는 단일 인스턴스 가정(read-modify-write). per-user UNIQUE(coupon_id,user_id)가 1인 중복을 막고, max 총량은 경합 시 소폭 초과 가능(허용, 후추 정밀화). 1인한도는 DB UNIQUE로 강제.

## Part 1~3 인터페이스 (이 계획이 호출)
- `Coupon`(code, days, grantType, validFrom, validUntil, maxRedemptions, perUserLimit, redeemedCount, status, source, memo, createdBy, id), `CouponRedemption(couponId, userId, grantedDays)` + var subscriptionId, createdAt
- `CouponRepository.findByCode`/`existsByCode`/`save`/`findById`/`findAll`. `CouponRedemptionRepository.existsByCouponIdAndUserId`/`findByCouponIdOrderByCreatedAtDesc`/`save`
- `SubscriptionRepository.findByUserId`. `BillingErrorCode.COUPON_*`. `CommonErrorCode.TOO_MANY_REQUESTS`
- `@RequiresAdmin`(kr.ai.flori.admin.gating.RequiresAdmin), `AdminAuditService.record(action, targetType, targetId, summary, metadata)`, `TenantContext.currentUserId()`/`currentUserIdOrNull()`
- `Paging.pageSize(page, size, max)`(admin 목록 페이지네이션 — 기존 유틸), `ClientContext.current()?.ip`(레이트리밋 IP)

## File Structure (Part 4)
- Modify: `billing/service/SubscriptionService.kt` (`applyFreeDays` 추가)
- Create: `billing/service/CouponService.kt`, `billing/support/CouponCodeGenerator.kt`
- Create: `billing/dto/CouponDtos.kt`
- Create: `billing/controller/CouponController.kt` (점주 redeem)
- Create: `billing/ratelimit/CouponRedeemRateLimitInterceptor.kt` + 등록(WebConfig)
- Create: `billing/service/AdminCouponService.kt`, `billing/controller/AdminCouponController.kt`
- Tests: `CouponRedeemTest.kt`, `AdminCouponTest.kt`

## 마일스톤
Part1 기반 ✅ · Part2 구독 ✅ · Part3 엔진 ✅ → **Part 4 쿠폰(이 문서)** → Part 5 웹훅·집계.

---

## Task 1: 점주 쿠폰 등록 (redeem + 검증4종 + 날짜적용 + 레이트리밋)

**Files:**
- Modify: `billing/service/SubscriptionService.kt` (`applyFreeDays`)
- Create: `billing/service/CouponService.kt`, `billing/dto/CouponDtos.kt`, `billing/controller/CouponController.kt`
- Create: `billing/ratelimit/CouponRedeemRateLimitInterceptor.kt`, 등록 config
- Test: `billing/CouponRedeemTest.kt`

**Interfaces:**
- Produces:
  - `SubscriptionService.applyFreeDays(userId: Long, days: Int): java.time.Instant?` (구독 있으면 밀고 새 nextBillingAt 반환, 없으면 null=pending)
  - `CouponService.redeem(code: String): RedeemResponse(grantedDays, nextBillingAt: Instant?, pending: Boolean)`
  - `POST /coupons/redeem` body `{ code }`

- [ ] **Step 1: 실패 테스트** (`CouponRedeemTest.kt`)

```kotlin
package kr.ai.flori.billing

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.billing.entity.Coupon
import kr.ai.flori.billing.entity.Subscription
import kr.ai.flori.billing.repository.CouponRepository
import kr.ai.flori.billing.repository.SubscriptionRepository
import kr.ai.flori.billing.service.CouponService
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.support.TestTenants
import kr.ai.flori.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant
import java.time.temporal.ChronoUnit

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class CouponRedeemTest {
    @Autowired lateinit var couponService: CouponService
    @Autowired lateinit var couponRepository: CouponRepository
    @Autowired lateinit var subscriptionRepository: SubscriptionRepository
    @Autowired lateinit var authService: AuthService
    @Autowired lateinit var tokenProvider: JwtTokenProvider
    @Autowired lateinit var userRepository: UserRepository

    @AfterEach fun cleanup() = TenantContext.clear()

    private fun user(): Long = TestTenants.bootstrap(authService, tokenProvider, userRepository)

    private fun coupon(code: String, days: Int = 30): Coupon =
        couponRepository.save(Coupon(code = code, days = days).apply { status = "ACTIVE" })

    @Test
    fun `구독 있는 유저 redeem시 nextBillingAt 가 days만큼 밀린다`() {
        val userId = user()
        val base = Instant.now().plus(10, ChronoUnit.DAYS)
        subscriptionRepository.save(
            Subscription(userId, "MONTHLY", "TRIALING", 14900, base).apply { currentPeriodEnd = base },
        )
        coupon("FLORI30", 30)

        val res = couponService.redeem("FLORI30")

        assertThat(res.pending).isFalse()
        assertThat(res.grantedDays).isEqualTo(30)
        val sub = subscriptionRepository.findByUserId(userId)!!
        assertThat(sub.nextBillingAt).isBetween(base.plus(29, ChronoUnit.DAYS), base.plus(31, ChronoUnit.DAYS))
        assertThat(couponRepository.findByCode("FLORI30")!!.redeemedCount).isEqualTo(1)
    }

    @Test
    fun `구독 없는 유저 redeem은 pending(subscription_id null)`() {
        user()
        coupon("FLORI30")
        val res = couponService.redeem("FLORI30")
        assertThat(res.pending).isTrue()
        assertThat(res.nextBillingAt).isNull()
    }

    @Test
    fun `없는 코드는 COUPON_NOT_FOUND`() {
        user()
        assertThatThrownBy { couponService.redeem("NOPE") }.isInstanceOf(AppException::class.java)
    }

    @Test
    fun `1인 중복 사용 차단`() {
        user()
        coupon("FLORI30")
        couponService.redeem("FLORI30")
        assertThatThrownBy { couponService.redeem("FLORI30") }.isInstanceOf(AppException::class.java)
    }

    @Test
    fun `폐기(DISABLED) 쿠폰 거부`() {
        user()
        couponRepository.save(Coupon(code = "DEAD", days = 10).apply { status = "DISABLED" })
        assertThatThrownBy { couponService.redeem("DEAD") }.isInstanceOf(AppException::class.java)
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests kr.ai.flori.billing.CouponRedeemTest`
Expected: FAIL — `CouponService`/`applyFreeDays` 미존재(컴파일 에러)

- [ ] **Step 3: SubscriptionService.applyFreeDays 추가**

`SubscriptionService.kt`에 메서드 추가(기존 KST 상수·ACTIVE_STATES 재사용):

```kotlin
    /** 무료 일수 적용: 활성 구독이면 nextBillingAt/period_end를 days만큼 밀고 새 nextBillingAt 반환. 없으면 null(pending). */
    @Transactional
    fun applyFreeDays(
        userId: Long,
        days: Int,
    ): Instant? {
        val sub = subscriptionRepository.findByUserId(userId) ?: return null
        if (sub.status !in ACTIVE_STATES) return null
        val next = ZonedDateTime.ofInstant(sub.nextBillingAt, KST).plusDays(days.toLong())
        sub.nextBillingAt = next.toInstant()
        sub.currentPeriodEnd = next.toInstant()
        return subscriptionRepository.save(sub).nextBillingAt
    }
```
(import `java.time.Instant` 이미 있음.)

- [ ] **Step 4: DTO** (`billing/dto/CouponDtos.kt`)

```kotlin
package kr.ai.flori.billing.dto

import jakarta.validation.constraints.NotBlank
import java.time.Instant

data class RedeemRequest(
    @field:NotBlank(message = "쿠폰 코드는 필수입니다")
    val code: String,
)

data class RedeemResponse(
    val grantedDays: Int,
    val nextBillingAt: Instant?,
    val pending: Boolean,
)
```

- [ ] **Step 5: CouponService** (`billing/service/CouponService.kt`)

```kotlin
package kr.ai.flori.billing.service

import kr.ai.flori.billing.dto.RedeemResponse
import kr.ai.flori.billing.entity.Coupon
import kr.ai.flori.billing.entity.CouponRedemption
import kr.ai.flori.billing.error.BillingErrorCode
import kr.ai.flori.billing.repository.CouponRedemptionRepository
import kr.ai.flori.billing.repository.CouponRepository
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.tenant.TenantContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** 점주 쿠폰 등록(redeem). 검증4종 → 기록 → 무료일수 적용(또는 pending). */
@Service
class CouponService(
    private val couponRepository: CouponRepository,
    private val redemptionRepository: CouponRedemptionRepository,
    private val subscriptionService: SubscriptionService,
) {
    @Transactional
    fun redeem(code: String): RedeemResponse {
        val userId = TenantContext.currentUserId()
        val coupon = couponRepository.findByCode(code) ?: throw AppException(BillingErrorCode.COUPON_NOT_FOUND)
        validate(coupon, userId)

        redemptionRepository.save(CouponRedemption(requireNotNull(coupon.id), userId, coupon.days))
        coupon.redeemedCount += 1
        couponRepository.save(coupon)

        val nextBillingAt = subscriptionService.applyFreeDays(userId, coupon.days)
        return RedeemResponse(grantedDays = coupon.days, nextBillingAt = nextBillingAt, pending = nextBillingAt == null)
    }

    private fun validate(
        coupon: Coupon,
        userId: Long,
    ) {
        if (coupon.status != "ACTIVE") throw AppException(BillingErrorCode.COUPON_DISABLED)
        val now = Instant.now()
        if (coupon.validFrom != null && now.isBefore(coupon.validFrom)) throw AppException(BillingErrorCode.COUPON_NOT_IN_PERIOD)
        if (coupon.validUntil != null && now.isAfter(coupon.validUntil)) throw AppException(BillingErrorCode.COUPON_NOT_IN_PERIOD)
        val max = coupon.maxRedemptions
        if (max != null && coupon.redeemedCount >= max) throw AppException(BillingErrorCode.COUPON_EXHAUSTED)
        if (redemptionRepository.existsByCouponIdAndUserId(requireNotNull(coupon.id), userId)) {
            throw AppException(BillingErrorCode.COUPON_EXHAUSTED, "이미 사용한 쿠폰입니다")
        }
    }
}
```

- [ ] **Step 6: Controller** (`billing/controller/CouponController.kt`)

```kotlin
package kr.ai.flori.billing.controller

import jakarta.validation.Valid
import kr.ai.flori.billing.dto.RedeemRequest
import kr.ai.flori.billing.dto.RedeemResponse
import kr.ai.flori.billing.service.CouponService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/coupons")
class CouponController(
    private val couponService: CouponService,
) {
    @PostMapping("/redeem")
    fun redeem(
        @Valid @RequestBody request: RedeemRequest,
    ): RedeemResponse = couponService.redeem(request.code)
}
```

- [ ] **Step 7: 레이트리밋 인터셉터** (`billing/ratelimit/CouponRedeemRateLimitInterceptor.kt`, WaitlistRateLimitInterceptor 패턴)

```kotlin
package kr.ai.flori.billing.ratelimit

import com.github.benmanes.caffeine.cache.Caffeine
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.error.CommonErrorCode
import kr.ai.flori.common.tenant.TenantContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/** 쿠폰 redeem 무차별 대입 방지. 유저(폴백 IP)당 윈도 제한. 단일 인스턴스 인메모리(Caffeine). */
@Component
class CouponRedeemRateLimitInterceptor(
    @Value("\${coupon.rate-limit.max-per-window:10}") private val maxPerWindow: Int,
    @Value("\${coupon.rate-limit.window-seconds:600}") windowSeconds: Long,
) : HandlerInterceptor {
    private val counters =
        Caffeine
            .newBuilder()
            .expireAfterWrite(Duration.ofSeconds(windowSeconds))
            .maximumSize(MAX_TRACKED)
            .build<String, AtomicInteger>()

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        if (!request.method.equals("POST", ignoreCase = true)) return true
        val key = TenantContext.currentUserIdOrNull()?.toString() ?: request.remoteAddr ?: "unknown"
        val count = counters.get(key) { AtomicInteger(0) }.incrementAndGet()
        if (count > maxPerWindow) throw AppException(CommonErrorCode.TOO_MANY_REQUESTS)
        return true
    }

    private companion object {
        const val MAX_TRACKED = 10_000L
    }
}
```
등록(`billing/config/`에 WebConfig 신규 또는 기존 config에): `registry.addInterceptor(couponRedeemRateLimitInterceptor).addPathPatterns("/coupons/redeem")`. **단, AdminWebConfig처럼 `WebMvcConfigurer`를 새로 만들 때 기존 인터셉터 등록과 충돌 없게 별도 config 클래스로.** 예:

```kotlin
package kr.ai.flori.billing.config

import kr.ai.flori.billing.ratelimit.CouponRedeemRateLimitInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class CouponWebConfig(
    private val interceptor: CouponRedeemRateLimitInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(interceptor).addPathPatterns("/coupons/redeem")
    }
}
```

- [ ] **Step 8: 통과 + 게이트**

Run: `./gradlew test --tests kr.ai.flori.billing.CouponRedeemTest` → PASS (5)
Run: `./gradlew check` → SUCCESSFUL. `git status` 클린.

- [ ] **Step 9: 커밋**

```bash
git add src/main/kotlin/kr/ai/flori/billing/service/SubscriptionService.kt src/main/kotlin/kr/ai/flori/billing/service/CouponService.kt src/main/kotlin/kr/ai/flori/billing/dto/CouponDtos.kt src/main/kotlin/kr/ai/flori/billing/controller/CouponController.kt src/main/kotlin/kr/ai/flori/billing/ratelimit src/main/kotlin/kr/ai/flori/billing/config/CouponWebConfig.kt src/test/kotlin/kr/ai/flori/billing/CouponRedeemTest.kt
git commit -m "feat(billing): 쿠폰 등록(redeem) + 검증4종 + 무료일수 적용 + 레이트리밋" -m "Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 2: 콘솔 쿠폰 발행 + 목록 (admin issue/list)

**Files:**
- Create: `billing/support/CouponCodeGenerator.kt`
- Create: `billing/service/AdminCouponService.kt`, `billing/controller/AdminCouponController.kt`
- Modify: `billing/dto/CouponDtos.kt` (issue/list DTO 추가)
- Test: `billing/AdminCouponTest.kt`

**Interfaces:**
- Produces:
  - `CouponCodeGenerator.generate(): String` (12자 Crockford Base32, `XXXX-XXXX-XXXX`)
  - `AdminCouponService.issue(req): CouponResponse`, `list(): List<CouponResponse>`
  - `CouponResponse(id, code, days, status, effectiveStatus, redeemedCount, maxRedemptions, perUserLimit, validFrom, validUntil, source, memo, createdAt)`
  - `POST /admin/coupons`, `GET /admin/coupons` (`@RequiresAdmin`)

- [ ] **Step 1: 실패 테스트** (`AdminCouponTest.kt`)

```kotlin
package kr.ai.flori.billing

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.billing.dto.CouponIssueRequest
import kr.ai.flori.billing.service.AdminCouponService
import kr.ai.flori.billing.support.CouponCodeGenerator
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.support.TestTenants
import kr.ai.flori.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class AdminCouponTest {
    @Autowired lateinit var adminCouponService: AdminCouponService
    @Autowired lateinit var generator: CouponCodeGenerator
    @Autowired lateinit var authService: AuthService
    @Autowired lateinit var tokenProvider: JwtTokenProvider
    @Autowired lateinit var userRepository: UserRepository

    @AfterEach fun cleanup() = TenantContext.clear()

    private fun admin(): Long = TestTenants.bootstrap(authService, tokenProvider, userRepository)

    @Test
    fun `코드 자동생성은 12자 영숫자(하이픈 제외) + 헷갈림문자 없음`() {
        val raw = generator.generate().replace("-", "")
        assertThat(raw).hasSize(12)
        assertThat(raw).matches("[0-9ABCDEFGHJKMNPQRSTVWXYZ]+") // I/L/O/U 제외
    }

    @Test
    fun `발행시 자동코드 + ACTIVE + created_by 기록`() {
        val adminId = admin()
        val res = adminCouponService.issue(
            CouponIssueRequest(code = null, days = 30, validFrom = null, validUntil = null, maxRedemptions = 100, perUserLimit = 1, source = "PROMO", memo = "테스트"),
        )
        assertThat(res.code).isNotBlank()
        assertThat(res.days).isEqualTo(30)
        assertThat(res.effectiveStatus).isEqualTo("ACTIVE")
        assertThat(adminCouponService.list().map { it.id }).contains(res.id)
    }

    @Test
    fun `커스텀 코드 발행`() {
        admin()
        val res = adminCouponService.issue(
            CouponIssueRequest(code = "OPEN2026", days = 14, validFrom = null, validUntil = null, maxRedemptions = null, perUserLimit = 1, source = "EVENT", memo = null),
        )
        assertThat(res.code).isEqualTo("OPEN2026")
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests kr.ai.flori.billing.AdminCouponTest`
Expected: FAIL — 미존재(컴파일 에러)

- [ ] **Step 3: 코드 생성기** (`billing/support/CouponCodeGenerator.kt`)

```kotlin
package kr.ai.flori.billing.support

import kr.ai.flori.billing.repository.CouponRepository
import org.springframework.stereotype.Component
import java.security.SecureRandom

/** 쿠폰 코드 자동생성: 12자 Crockford Base32(I/L/O/U 제외), XXXX-XXXX-XXXX. 충돌 시 재생성. */
@Component
class CouponCodeGenerator(
    private val couponRepository: CouponRepository,
) {
    private val random = SecureRandom()

    fun generate(): String {
        repeat(MAX_TRIES) {
            val code = randomCode()
            if (!couponRepository.existsByCode(code)) return code
        }
        error("쿠폰 코드 생성 실패(충돌 과다)")
    }

    private fun randomCode(): String {
        val chars = CharArray(LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] }
        val raw = String(chars)
        return "${raw.substring(0, 4)}-${raw.substring(4, 8)}-${raw.substring(8, 12)}"
    }

    private companion object {
        const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        const val LENGTH = 12
        const val MAX_TRIES = 10
    }
}
```

- [ ] **Step 4: DTO 추가** (`billing/dto/CouponDtos.kt`에 append)

```kotlin
data class CouponIssueRequest(
    val code: String? = null,
    @field:jakarta.validation.constraints.Min(1, message = "무료 일수는 1 이상")
    val days: Int,
    val validFrom: java.time.Instant? = null,
    val validUntil: java.time.Instant? = null,
    val maxRedemptions: Int? = null,
    val perUserLimit: Int = 1,
    val source: String = "PROMO",
    val memo: String? = null,
)

data class CouponResponse(
    val id: Long,
    val code: String,
    val days: Int,
    val status: String,
    val effectiveStatus: String,
    val redeemedCount: Int,
    val maxRedemptions: Int?,
    val perUserLimit: Int,
    val validFrom: java.time.Instant?,
    val validUntil: java.time.Instant?,
    val source: String,
    val memo: String?,
    val createdAt: java.time.Instant,
) {
    companion object {
        fun of(
            c: kr.ai.flori.billing.entity.Coupon,
            now: java.time.Instant,
        ): CouponResponse =
            CouponResponse(
                id = requireNotNull(c.id),
                code = c.code,
                days = c.days,
                status = c.status,
                effectiveStatus = effective(c, now),
                redeemedCount = c.redeemedCount,
                maxRedemptions = c.maxRedemptions,
                perUserLimit = c.perUserLimit,
                validFrom = c.validFrom,
                validUntil = c.validUntil,
                source = c.source,
                memo = c.memo,
                createdAt = c.createdAt,
            )

        private fun effective(
            c: kr.ai.flori.billing.entity.Coupon,
            now: java.time.Instant,
        ): String =
            when {
                c.status == "DISABLED" -> "DISABLED"
                c.validUntil != null && now.isAfter(c.validUntil) -> "EXPIRED"
                c.maxRedemptions != null && c.redeemedCount >= c.maxRedemptions!! -> "EXHAUSTED"
                else -> "ACTIVE"
            }
    }
}
```

- [ ] **Step 5: AdminCouponService** (`billing/service/AdminCouponService.kt`)

```kotlin
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
```

- [ ] **Step 6: Controller** (`billing/controller/AdminCouponController.kt`)

```kotlin
package kr.ai.flori.billing.controller

import jakarta.validation.Valid
import kr.ai.flori.admin.gating.RequiresAdmin
import kr.ai.flori.billing.dto.CouponIssueRequest
import kr.ai.flori.billing.dto.CouponResponse
import kr.ai.flori.billing.service.AdminCouponService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/coupons")
@RequiresAdmin
class AdminCouponController(
    private val adminCouponService: AdminCouponService,
) {
    @GetMapping
    fun list(): List<CouponResponse> = adminCouponService.list()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun issue(
        @Valid @RequestBody request: CouponIssueRequest,
    ): CouponResponse = adminCouponService.issue(request)
}
```

- [ ] **Step 7: 통과 + 게이트**

Run: `./gradlew test --tests kr.ai.flori.billing.AdminCouponTest` → PASS (3)
Run: `./gradlew check` → SUCCESSFUL. `git status` 클린.

- [ ] **Step 8: 커밋**

```bash
git add src/main/kotlin/kr/ai/flori/billing/support/CouponCodeGenerator.kt src/main/kotlin/kr/ai/flori/billing/service/AdminCouponService.kt src/main/kotlin/kr/ai/flori/billing/controller/AdminCouponController.kt src/main/kotlin/kr/ai/flori/billing/dto/CouponDtos.kt src/test/kotlin/kr/ai/flori/billing/AdminCouponTest.kt
git commit -m "feat(billing): 콘솔 쿠폰 발행+목록 (자동코드·감사로그)" -m "Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 3: 콘솔 쿠폰 상세(사용현황) + 폐기 (admin detail/disable)

**Files:**
- Modify: `billing/service/AdminCouponService.kt` (`detail`, `disable`)
- Modify: `billing/controller/AdminCouponController.kt` (엔드포인트)
- Modify: `billing/dto/CouponDtos.kt` (`CouponDetailResponse`, `RedemptionRow`)
- Test: `billing/AdminCouponTest.kt` (케이스 추가)

**Interfaces:**
- Produces:
  - `AdminCouponService.detail(id): CouponDetailResponse(coupon, redemptions)`, `disable(id): CouponResponse`
  - `GET /admin/coupons/{id}`, `POST /admin/coupons/{id}/disable`

- [ ] **Step 1: 실패 테스트** (`AdminCouponTest.kt`에 추가)

```kotlin
    @Test
    fun `상세는 쿠폰 + 사용현황(redemptions) 반환`() {
        admin()
        val issued = adminCouponService.issue(
            CouponIssueRequest("CODEX", 10, null, null, null, 1, "PROMO", null),
        )
        val detail = adminCouponService.detail(issued.id)
        assertThat(detail.coupon.code).isEqualTo("CODEX")
        assertThat(detail.redemptions).isEmpty()
    }

    @Test
    fun `폐기시 DISABLED + 감사로그`() {
        admin()
        val issued = adminCouponService.issue(
            CouponIssueRequest("KILLME", 10, null, null, null, 1, "PROMO", null),
        )
        val disabled = adminCouponService.disable(issued.id)
        assertThat(disabled.effectiveStatus).isEqualTo("DISABLED")
    }
```
(import: `kr.ai.flori.billing.dto.CouponIssueRequest` 이미 있음.)

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests kr.ai.flori.billing.AdminCouponTest`
Expected: FAIL — `detail`/`disable`/`CouponDetailResponse` 미존재

- [ ] **Step 3: DTO 추가** (`CouponDtos.kt`)

```kotlin
data class RedemptionRow(
    val userId: Long,
    val grantedDays: Int,
    val redeemedAt: java.time.Instant,
)

data class CouponDetailResponse(
    val coupon: CouponResponse,
    val redemptions: List<RedemptionRow>,
)
```

- [ ] **Step 4: 서비스 메서드 추가** (`AdminCouponService.kt`; `CouponRedemptionRepository` 주입 추가)

```kotlin
    // 생성자에 추가: private val redemptionRepository: CouponRedemptionRepository

    @Transactional(readOnly = true)
    fun detail(id: Long): CouponDetailResponse {
        val coupon = couponRepository.findById(id).orElseThrow { AppException(CommonErrorCode.NOT_FOUND, "쿠폰을 찾을 수 없습니다") }
        val rows =
            redemptionRepository.findByCouponIdOrderByCreatedAtDesc(id).map {
                RedemptionRow(it.userId, it.grantedDays, it.createdAt)
            }
        return CouponDetailResponse(CouponResponse.of(coupon, Instant.now()), rows)
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
```
(import: `kr.ai.flori.billing.dto.CouponDetailResponse`, `kr.ai.flori.billing.dto.RedemptionRow`, `kr.ai.flori.billing.repository.CouponRedemptionRepository`.)

- [ ] **Step 5: Controller 엔드포인트 추가** (`AdminCouponController.kt`)

```kotlin
    @GetMapping("/{id}")
    fun detail(
        @org.springframework.web.bind.annotation.PathVariable id: Long,
    ): kr.ai.flori.billing.dto.CouponDetailResponse = adminCouponService.detail(id)

    @PostMapping("/{id}/disable")
    fun disable(
        @org.springframework.web.bind.annotation.PathVariable id: Long,
    ): CouponResponse = adminCouponService.disable(id)
```

- [ ] **Step 6: 통과 + 게이트**

Run: `./gradlew test --tests kr.ai.flori.billing.AdminCouponTest` → PASS (5)
Run: `./gradlew check` → SUCCESSFUL. `git status` 클린.

- [ ] **Step 7: 커밋**

```bash
git add src/main/kotlin/kr/ai/flori/billing/service/AdminCouponService.kt src/main/kotlin/kr/ai/flori/billing/controller/AdminCouponController.kt src/main/kotlin/kr/ai/flori/billing/dto/CouponDtos.kt src/test/kotlin/kr/ai/flori/billing/AdminCouponTest.kt
git commit -m "feat(billing): 콘솔 쿠폰 상세(사용현황)+폐기 (감사로그)" -m "Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Part 4 완료 검증
- [ ] `./gradlew check` BUILD SUCCESSFUL + `git status` 클린.

## Self-Review (Part 4)
- **스펙 커버리지**: §6-A redeem → T1. §6-B 발행/목록/상세/폐기 → T2·T3. 검증4종·코드생성·effective status·감사로그·레이트리밋 모두 포함.
- **Placeholder 스캔**: 전 Step 실제 코드/명령/기대값. TBD 없음.
- **타입 일관성**: `RedeemRequest/RedeemResponse`, `CouponIssueRequest/CouponResponse/CouponDetailResponse/RedemptionRow`, `applyFreeDays(userId,days):Instant?`, `CouponCodeGenerator.generate()` 일치. Part1~3 시그니처(엔티티/리포지토리/AdminAuditService/RequiresAdmin) 정확 호출.
- **알려진 함정(구현자 필독)**: ① 모킹 불필요(redeem은 외부호출 없음, BillingClient 무관). ② `@RequiresAdmin`은 컨트롤러 레벨 — 서비스 단위테스트는 인터셉터 안 거치므로 admin 판정 없이 동작(서비스 테스트는 OK). ③ ktlint 준수. ④ redeemed_count read-modify-write 단일인스턴스 가정(이월 부채). ⑤ 커밋 전 `./gradlew check` + git status clean.
