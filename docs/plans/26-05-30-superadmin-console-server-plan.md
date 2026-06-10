# Superadmin Console — Server (Kotlin BFF) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add cross-tenant, `@RequiresAdmin`-guarded REST endpoints under `/admin/**` for operator stats, business-verification approval, user/subscription browse, and AI health proxy.

**Architecture:** New `kr.ai.flori.admin` package. An `AdminInterceptor` (mirror of `verification/gating/BusinessVerifiedInterceptor`) re-checks `User.isAdmin` from DB for any `@RequiresAdmin` handler → 403 otherwise. Cross-tenant aggregation uses `JdbcTemplate` native SQL with **no `user_id` filter** (intentional; protected only by the guard). Verification approve/reject reuses the entity's existing `approve()`/`reject(reason)` domain methods and publishes a review event → Discord + push (mirror of submit listener). AI health proxies via Spring `RestClient`.

**Tech Stack:** Kotlin, Spring Boot 3, Spring Security (stateless JWT), JPA + JdbcTemplate, JUnit5 + MockMvc + Zonky embedded Postgres.

**Spec:** `flori-ai/web/docs/plans/26-05-30-superadmin-console-design.md`

**Conventions to follow:**
- Error codes implement `kr.ai.flori.common.error.ErrorCode` (`E-{DOMAIN}-{NNN}`); domain codes in `<domain>/error`.
- Identity ALWAYS from `TenantContext.currentUserId()` — never from request body.
- Tests: `@SpringBootTest @AutoConfigureMockMvc @AutoConfigureEmbeddedDatabase(provider = ZONKY)`, accounts via `kr.ai.flori.support.TestAccounts.register(authService, tokenProvider)`. Make an admin by loading the user via `UserRepository`, setting `isAdmin = true`, saving.
- Build/test: `./gradlew test` (single test: `./gradlew test --tests "kr.ai.flori.admin.*"`). Lint: `./gradlew ktlintCheck`.

---

## File Structure

```
src/main/kotlin/kr/ai/flori/admin/
├── gating/RequiresAdmin.kt              # annotation
├── gating/AdminInterceptor.kt           # is_admin DB re-check → 403
├── gating/AdminWebConfig.kt             # register interceptor
├── error/AdminErrorCode.kt              # FORBIDDEN_NOT_ADMIN, VERIFICATION_NOT_FOUND, INVALID_VERIFICATION_STATE
├── dto/AdminStatsDtos.kt                # AdminOverviewResponse + nested counts
├── dto/AdminVerificationDtos.kt         # AdminVerificationResponse, RejectRequest
├── dto/AdminUserDtos.kt                 # AdminUserRow, AdminUserDetail, SetActiveRequest, page wrapper
├── dto/AdminSubscriptionDtos.kt         # AdminSubscriptionRow
├── dto/AdminAiHealthDtos.kt             # AiHealthResponse, AiHealthTarget
├── service/AdminStatsService.kt
├── service/AdminVerificationService.kt
├── service/AdminUserService.kt
├── service/AdminSubscriptionService.kt
├── service/AiHealthService.kt
├── config/AiHealthProperties.kt         # ai.health.* binding
├── event/BusinessVerificationReviewedEvent.kt
├── listener/BusinessVerificationReviewedListener.kt
└── controller/
    ├── AdminController.kt               # GET /admin/me
    ├── AdminStatsController.kt
    ├── AdminVerificationController.kt
    ├── AdminUserController.kt
    ├── AdminSubscriptionController.kt
    └── AdminHealthController.kt
```
Modify: `verification/repository/BusinessVerificationRepository.kt`, `src/main/resources/application.yml`.

---

## Task 1: `@RequiresAdmin` annotation + interceptor + registration

**Files:**
- Create: `src/main/kotlin/kr/ai/flori/admin/gating/RequiresAdmin.kt`
- Create: `src/main/kotlin/kr/ai/flori/admin/gating/AdminInterceptor.kt`
- Create: `src/main/kotlin/kr/ai/flori/admin/gating/AdminWebConfig.kt`
- Create: `src/main/kotlin/kr/ai/flori/admin/error/AdminErrorCode.kt`
- Test: `src/test/kotlin/kr/ai/flori/admin/AdminGatingIntegrationTest.kt`

- [ ] **Step 1: Create the error code**

```kotlin
package kr.ai.flori.admin.error

import kr.ai.flori.common.error.ErrorCode
import org.springframework.http.HttpStatus

/** 운영 콘솔(admin) 전용 에러 코드. */
enum class AdminErrorCode(
    override val code: String,
    override val status: HttpStatus,
    override val defaultMessage: String,
) : ErrorCode {
    FORBIDDEN_NOT_ADMIN("E-ADM-001", HttpStatus.FORBIDDEN, "운영자 권한이 필요합니다"),
    VERIFICATION_NOT_FOUND("E-ADM-002", HttpStatus.NOT_FOUND, "사업자 인증 신청을 찾을 수 없습니다"),
    INVALID_VERIFICATION_STATE("E-ADM-003", HttpStatus.CONFLICT, "이미 처리된 신청입니다"),
}
```

- [ ] **Step 2: Create the annotation**

```kotlin
package kr.ai.flori.admin.gating

/**
 * 운영자 전용 엔드포인트 표시. 컨트롤러 메서드/클래스에 붙이면 [AdminInterceptor]가
 * 진입 전 User.isAdmin 을 강제하고, 아니면 403.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequiresAdmin
```

- [ ] **Step 3: Create the interceptor** (mirror `BusinessVerifiedInterceptor`)

```kotlin
package kr.ai.flori.admin.gating

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kr.ai.flori.admin.error.AdminErrorCode
import kr.ai.flori.common.error.AppException
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.user.repository.UserRepository
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

/**
 * @RequiresAdmin 핸들러 진입 전 현재 사용자의 is_admin 을 강제한다.
 * JWT 필터(TenantContext set) 이후 실행. 미인증/비운영자면 403.
 */
@Component
class AdminInterceptor(
    private val userRepository: ObjectProvider<UserRepository>,
) : HandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        if (handler is HandlerMethod && requiresAdmin(handler)) {
            val userId = TenantContext.currentUserId()
            val user =
                userRepository.getObject().findById(userId).orElse(null)
                    ?: throw AppException(AdminErrorCode.FORBIDDEN_NOT_ADMIN)
            if (!user.isAdmin) throw AppException(AdminErrorCode.FORBIDDEN_NOT_ADMIN)
        }
        return true
    }

    private fun requiresAdmin(handler: HandlerMethod): Boolean =
        handler.hasMethodAnnotation(RequiresAdmin::class.java) ||
            handler.beanType.isAnnotationPresent(RequiresAdmin::class.java)
}
```
> Note: `TenantContext.currentUserId()` throws if unauthenticated; `/admin/**` is not in the SecurityConfig permitAll list, so unauthenticated requests are already rejected (401) by Spring Security before reaching the interceptor.

- [ ] **Step 4: Register the interceptor**

```kotlin
package kr.ai.flori.admin.gating

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/** 운영자 게이팅 인터셉터 등록(도메인 자체 설정으로 캡슐화). */
@Configuration
class AdminWebConfig(
    private val adminInterceptor: AdminInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(adminInterceptor)
    }
}
```

- [ ] **Step 5: Create `AdminController` (GET /admin/me) so the gate has a target**

File: `src/main/kotlin/kr/ai/flori/admin/controller/AdminController.kt`
```kotlin
package kr.ai.flori.admin.controller

import kr.ai.flori.admin.gating.RequiresAdmin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 운영 콘솔 진입점. @RequiresAdmin 가드 확인용. */
@RestController
@RequestMapping("/admin")
@RequiresAdmin
class AdminController {
    @GetMapping("/me")
    fun me(): Map<String, Boolean> = mapOf("isAdmin" to true)
}
```

- [ ] **Step 6: Write the failing gating test**

```kotlin
package kr.ai.flori.admin

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.support.TestAccounts
import kr.ai.flori.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
@AutoConfigureMockMvc
class AdminGatingIntegrationTest {
    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var authService: AuthService
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var userRepository: UserRepository

    private fun makeAdmin(userId: Long) {
        val user = userRepository.findById(userId).orElseThrow()
        user.isAdmin = true
        userRepository.save(user)
    }

    @Test
    fun `비운영자는 admin me 가 403`() {
        val token = TestAccounts.register(authService, tokenProvider).accessToken
        mockMvc.get("/admin/me") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isForbidden() } }
    }

    @Test
    fun `운영자는 admin me 가 200`() {
        val tokens = TestAccounts.register(authService, tokenProvider)
        makeAdmin(tokenProvider.parse(tokens.accessToken)!!.userId)
        mockMvc.get("/admin/me") { header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}") }
            .andExpect { status { isOk() } }
    }

    @Test
    fun `미인증은 admin me 가 401`() {
        mockMvc.get("/admin/me").andExpect { status { isUnauthorized() } }
    }
}
```

- [ ] **Step 7: Run — expect compile/pass**

Run: `./gradlew test --tests "kr.ai.flori.admin.AdminGatingIntegrationTest"`
Expected: PASS (3 tests).

- [ ] **Step 8: Commit**

```bash
git add src/main/kotlin/kr/ai/flori/admin src/test/kotlin/kr/ai/flori/admin
git commit -m "feat(admin): @RequiresAdmin 게이트 + /admin/me 진입점"
```

---

## Task 2: Cross-tenant stats — `GET /admin/stats/overview`

**Files:**
- Create: `src/main/kotlin/kr/ai/flori/admin/dto/AdminStatsDtos.kt`
- Create: `src/main/kotlin/kr/ai/flori/admin/service/AdminStatsService.kt`
- Create: `src/main/kotlin/kr/ai/flori/admin/controller/AdminStatsController.kt`
- Test: `src/test/kotlin/kr/ai/flori/admin/AdminStatsIntegrationTest.kt`

- [ ] **Step 1: DTOs**

```kotlin
package kr.ai.flori.admin.dto

data class UserCounts(val total: Long, val active: Long, val onboarded: Long)
data class SalesCounts(val entryCount: Long, val totalAmount: Long, val last30dCount: Long)
data class SubscriptionCounts(val active: Long, val inGrace: Long, val expired: Long, val none: Long)
data class VerificationCounts(val pending: Long, val approved: Long, val rejected: Long)

data class AdminOverviewResponse(
    val users: UserCounts,
    val sales: SalesCounts,
    val subscriptions: SubscriptionCounts,
    val verifications: VerificationCounts,
)
```

- [ ] **Step 2: Service (cross-tenant native SQL, NO user_id filter)**

```kotlin
package kr.ai.flori.admin.service

import kr.ai.flori.admin.dto.AdminOverviewResponse
import kr.ai.flori.admin.dto.SalesCounts
import kr.ai.flori.admin.dto.SubscriptionCounts
import kr.ai.flori.admin.dto.UserCounts
import kr.ai.flori.admin.dto.VerificationCounts
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 운영 콘솔 cross-tenant 집계. 의도적으로 user_id 미필터(전 테넌트).
 * @RequiresAdmin 가드 하위에서만 호출된다.
 */
@Service
class AdminStatsService(
    private val jdbc: JdbcTemplate,
) {
    @Transactional(readOnly = true)
    fun overview(): AdminOverviewResponse =
        AdminOverviewResponse(
            users = userCounts(),
            sales = salesCounts(),
            subscriptions = subscriptionCounts(),
            verifications = verificationCounts(),
        )

    private fun userCounts(): UserCounts =
        jdbc.queryForObject(
            """
            SELECT
              COUNT(*) AS total,
              COUNT(*) FILTER (WHERE is_active) AS active,
              COUNT(*) FILTER (WHERE EXISTS (SELECT 1 FROM user_profiles p WHERE p.user_id = u.id)) AS onboarded
            FROM users u
            """.trimIndent(),
        ) { rs, _ -> UserCounts(rs.getLong("total"), rs.getLong("active"), rs.getLong("onboarded")) }!!

    private fun salesCounts(): SalesCounts =
        jdbc.queryForObject(
            """
            SELECT
              COUNT(*) AS entry_count,
              COALESCE(SUM(amount) FILTER (WHERE payment_method <> 'unpaid'), 0) AS total_amount,
              COUNT(*) FILTER (WHERE date >= CURRENT_DATE - INTERVAL '30 days') AS last30d
            FROM sales
            """.trimIndent(),
        ) { rs, _ -> SalesCounts(rs.getLong("entry_count"), rs.getLong("total_amount"), rs.getLong("last30d")) }!!

    private fun subscriptionCounts(): SubscriptionCounts =
        jdbc.queryForObject(
            """
            SELECT
              COUNT(*) FILTER (WHERE status = 'active') AS active,
              COUNT(*) FILTER (WHERE status = 'in_grace') AS in_grace,
              COUNT(*) FILTER (WHERE status = 'expired') AS expired,
              COUNT(*) FILTER (WHERE status = 'none') AS none
            FROM subscriptions
            """.trimIndent(),
        ) { rs, _ ->
            SubscriptionCounts(rs.getLong("active"), rs.getLong("in_grace"), rs.getLong("expired"), rs.getLong("none"))
        }!!

    private fun verificationCounts(): VerificationCounts =
        jdbc.queryForObject(
            """
            SELECT
              COUNT(*) FILTER (WHERE status = 'PENDING') AS pending,
              COUNT(*) FILTER (WHERE status = 'APPROVED') AS approved,
              COUNT(*) FILTER (WHERE status = 'REJECTED') AS rejected
            FROM business_verifications
            """.trimIndent(),
        ) { rs, _ -> VerificationCounts(rs.getLong("pending"), rs.getLong("approved"), rs.getLong("rejected")) }!!
}
```

- [ ] **Step 3: Controller**

```kotlin
package kr.ai.flori.admin.controller

import kr.ai.flori.admin.dto.AdminOverviewResponse
import kr.ai.flori.admin.gating.RequiresAdmin
import kr.ai.flori.admin.service.AdminStatsService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/stats")
@RequiresAdmin
class AdminStatsController(
    private val statsService: AdminStatsService,
) {
    @GetMapping("/overview")
    fun overview(): AdminOverviewResponse = statsService.overview()
}
```

- [ ] **Step 4: Test**

```kotlin
package kr.ai.flori.admin

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.support.TestAccounts
import kr.ai.flori.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
@AutoConfigureMockMvc
class AdminStatsIntegrationTest {
    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var authService: AuthService
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var userRepository: UserRepository

    @Test
    fun `운영자는 overview 집계를 받는다 — 최소 가입자 1명`() {
        val tokens = TestAccounts.register(authService, tokenProvider)
        val user = userRepository.findById(tokenProvider.parse(tokens.accessToken)!!.userId).orElseThrow()
        user.isAdmin = true
        userRepository.save(user)

        mockMvc.get("/admin/stats/overview") { header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}") }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.users.total") { value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)) } }
            .andExpect { jsonPath("$.verifications.pending") { exists() } }
    }
}
```

- [ ] **Step 5: Run** — `./gradlew test --tests "kr.ai.flori.admin.AdminStatsIntegrationTest"` → PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/kr/ai/flori/admin/dto/AdminStatsDtos.kt \
        src/main/kotlin/kr/ai/flori/admin/service/AdminStatsService.kt \
        src/main/kotlin/kr/ai/flori/admin/controller/AdminStatsController.kt \
        src/test/kotlin/kr/ai/flori/admin/AdminStatsIntegrationTest.kt
git commit -m "feat(admin): cross-tenant 통계 overview 엔드포인트"
```

---

## Task 3: Business verification admin — list + approve/reject + review event

**Files:**
- Modify: `src/main/kotlin/kr/ai/flori/verification/repository/BusinessVerificationRepository.kt`
- Create: `src/main/kotlin/kr/ai/flori/admin/dto/AdminVerificationDtos.kt`
- Create: `src/main/kotlin/kr/ai/flori/admin/event/BusinessVerificationReviewedEvent.kt`
- Create: `src/main/kotlin/kr/ai/flori/admin/listener/BusinessVerificationReviewedListener.kt`
- Create: `src/main/kotlin/kr/ai/flori/admin/service/AdminVerificationService.kt`
- Create: `src/main/kotlin/kr/ai/flori/admin/controller/AdminVerificationController.kt`
- Test: `src/test/kotlin/kr/ai/flori/admin/AdminVerificationIntegrationTest.kt`

- [ ] **Step 1: Add repository finders**

Add to `BusinessVerificationRepository` (`org.springframework.data.domain.Pageable`/`Page` imports):
```kotlin
    /** 운영 콘솔: 상태별 신청 목록(최신순). cross-tenant. */
    fun findByStatusOrderByCreatedAtDesc(
        status: BusinessVerificationStatuses,
        pageable: org.springframework.data.domain.Pageable,
    ): org.springframework.data.domain.Page<BusinessVerification>
```

- [ ] **Step 2: DTOs**

```kotlin
package kr.ai.flori.admin.dto

import jakarta.validation.constraints.NotBlank
import java.time.Instant

data class AdminVerificationResponse(
    val id: Long,
    val userId: Long,
    val businessNumber: String,
    val businessName: String,
    val representativeName: String,
    val businessLicenseUrl: String,
    val status: String,
    val rejectReason: String?,
    val submittedAt: Instant?,
    val reviewedAt: Instant?,
)

data class AdminVerificationRejectRequest(
    @field:NotBlank(message = "거절 사유는 필수입니다")
    val reason: String,
)
```

- [ ] **Step 3: Review event + listener (mirror submit listener; Discord notify)**

```kotlin
package kr.ai.flori.admin.event

/** 운영자가 사업자 인증을 승인/거절했을 때 발행. approved=false면 reason 필수. */
data class BusinessVerificationReviewedEvent(
    val userId: Long,
    val businessName: String,
    val approved: Boolean,
    val reason: String?,
)
```
```kotlin
package kr.ai.flori.admin.listener

import kr.ai.flori.admin.event.BusinessVerificationReviewedEvent
import kr.ai.flori.common.notification.discord.DiscordChannel
import kr.ai.flori.common.notification.discord.DiscordMessage
import kr.ai.flori.common.notification.discord.DiscordNotifier
import kr.ai.flori.common.util.KST
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** 인증 심사 결과 → Discord 알림. DB 커밋 후 발송. */
@Component
class BusinessVerificationReviewedListener(
    private val discordNotifier: DiscordNotifier,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: BusinessVerificationReviewedEvent) {
        val now = ZonedDateTime.now(KST).format(TIMESTAMP_FORMAT)
        val verdict = if (event.approved) "승인 ✅" else "거절 ❌"
        val message =
            """
            **[사업자 인증 심사 $verdict]**
            - 일시: $now
            - userId: ${event.userId}
            - 상호: ${event.businessName}
            ${if (!event.approved) "- 사유: ${event.reason}" else ""}
            """.trimIndent()
        discordNotifier.notify(DiscordChannel.VERIFICATION, DiscordMessage.of(message))
    }

    private companion object {
        val TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
```

- [ ] **Step 4: Service**

```kotlin
package kr.ai.flori.admin.service

import kr.ai.flori.admin.dto.AdminVerificationResponse
import kr.ai.flori.admin.error.AdminErrorCode
import kr.ai.flori.admin.event.BusinessVerificationReviewedEvent
import kr.ai.flori.common.error.AppException
import kr.ai.flori.verification.domain.BusinessVerificationStatuses
import kr.ai.flori.verification.entity.BusinessVerification
import kr.ai.flori.verification.repository.BusinessVerificationRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 운영 콘솔 사업자 인증 심사. cross-tenant — @RequiresAdmin 하위에서만 호출. */
@Service
class AdminVerificationService(
    private val repository: BusinessVerificationRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional(readOnly = true)
    fun list(status: BusinessVerificationStatuses, page: Int, size: Int): List<AdminVerificationResponse> =
        repository.findByStatusOrderByCreatedAtDesc(status, PageRequest.of(page, size))
            .content.map { it.toResponse() }

    @Transactional
    fun approve(id: Long): AdminVerificationResponse {
        val v = load(id)
        if (v.status != BusinessVerificationStatuses.PENDING) throw AppException(AdminErrorCode.INVALID_VERIFICATION_STATE)
        v.approve()
        repository.save(v)
        eventPublisher.publishEvent(BusinessVerificationReviewedEvent(v.userId, v.businessName, true, null))
        return v.toResponse()
    }

    @Transactional
    fun reject(id: Long, reason: String): AdminVerificationResponse {
        val v = load(id)
        if (v.status != BusinessVerificationStatuses.PENDING) throw AppException(AdminErrorCode.INVALID_VERIFICATION_STATE)
        v.reject(reason)
        repository.save(v)
        eventPublisher.publishEvent(BusinessVerificationReviewedEvent(v.userId, v.businessName, false, reason))
        return v.toResponse()
    }

    private fun load(id: Long): BusinessVerification =
        repository.findById(id).orElseThrow { AppException(AdminErrorCode.VERIFICATION_NOT_FOUND) }

    private fun BusinessVerification.toResponse() =
        AdminVerificationResponse(
            id = id!!,
            userId = userId,
            businessNumber = businessNumber,
            businessName = businessName,
            representativeName = representativeName,
            businessLicenseUrl = businessLicenseUrl,
            status = status.name,
            rejectReason = rejectReason,
            submittedAt = createdAt,
            reviewedAt = reviewedAt,
        )
}
```

- [ ] **Step 5: Controller**

```kotlin
package kr.ai.flori.admin.controller

import jakarta.validation.Valid
import kr.ai.flori.admin.dto.AdminVerificationRejectRequest
import kr.ai.flori.admin.dto.AdminVerificationResponse
import kr.ai.flori.admin.gating.RequiresAdmin
import kr.ai.flori.admin.service.AdminVerificationService
import kr.ai.flori.verification.domain.BusinessVerificationStatuses
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/verifications")
@RequiresAdmin
class AdminVerificationController(
    private val service: AdminVerificationService,
) {
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "PENDING") status: BusinessVerificationStatuses,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): List<AdminVerificationResponse> = service.list(status, page, size)

    @PostMapping("/{id}/approve")
    fun approve(@PathVariable id: Long): AdminVerificationResponse = service.approve(id)

    @PostMapping("/{id}/reject")
    fun reject(
        @PathVariable id: Long,
        @Valid @RequestBody request: AdminVerificationRejectRequest,
    ): AdminVerificationResponse = service.reject(id, request.reason)
}
```

- [ ] **Step 6: Test (list PENDING, approve transitions to APPROVED, double-approve → 409)**

```kotlin
package kr.ai.flori.admin

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.support.TestAccounts
import kr.ai.flori.user.repository.UserRepository
import kr.ai.flori.verification.entity.BusinessVerification
import kr.ai.flori.verification.repository.BusinessVerificationRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
@AutoConfigureMockMvc
class AdminVerificationIntegrationTest {
    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var authService: AuthService
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var verificationRepository: BusinessVerificationRepository

    private fun adminToken(): String {
        val tokens = TestAccounts.register(authService, tokenProvider)
        val user = userRepository.findById(tokenProvider.parse(tokens.accessToken)!!.userId).orElseThrow()
        user.isAdmin = true
        userRepository.save(user)
        return tokens.accessToken
    }

    private fun pendingFor(): Long {
        val tokens = TestAccounts.register(authService, tokenProvider)
        val uid = tokenProvider.parse(tokens.accessToken)!!.userId
        return verificationRepository.save(
            BusinessVerification(uid, "1234567890", "플로리", "홍길동",
                "https://cdn.example.com/business-licenses/$uid/a.jpg"),
        ).id!!
    }

    @Test
    fun `운영자는 PENDING 목록을 조회한다`() {
        val token = adminToken()
        pendingFor()
        mockMvc.get("/admin/verifications?status=PENDING") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$[0].status") { value("PENDING") } }
    }

    @Test
    fun `승인하면 APPROVED 로 전이하고 재승인은 409`() {
        val token = adminToken()
        val id = pendingFor()
        mockMvc.post("/admin/verifications/$id/approve") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.status") { value("APPROVED") } }
        mockMvc.post("/admin/verifications/$id/approve") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isConflict() } }
    }
}
```

- [ ] **Step 7: Run** — `./gradlew test --tests "kr.ai.flori.admin.AdminVerificationIntegrationTest"` → PASS

- [ ] **Step 8: Commit**

```bash
git add src/main/kotlin/kr/ai/flori/admin src/main/kotlin/kr/ai/flori/verification/repository/BusinessVerificationRepository.kt src/test/kotlin/kr/ai/flori/admin/AdminVerificationIntegrationTest.kt
git commit -m "feat(admin): 사업자 인증 심사(목록/승인/거절) + 리뷰 알림"
```

---

## Task 4: User list/detail + is_active toggle; subscription list

**Files:**
- Create: `src/main/kotlin/kr/ai/flori/admin/dto/AdminUserDtos.kt`
- Create: `src/main/kotlin/kr/ai/flori/admin/dto/AdminSubscriptionDtos.kt`
- Create: `src/main/kotlin/kr/ai/flori/admin/service/AdminUserService.kt`
- Create: `src/main/kotlin/kr/ai/flori/admin/service/AdminSubscriptionService.kt`
- Create: `src/main/kotlin/kr/ai/flori/admin/controller/AdminUserController.kt`
- Create: `src/main/kotlin/kr/ai/flori/admin/controller/AdminSubscriptionController.kt`
- Test: `src/test/kotlin/kr/ai/flori/admin/AdminUserIntegrationTest.kt`

- [ ] **Step 1: DTOs**

```kotlin
package kr.ai.flori.admin.dto

import jakarta.validation.constraints.NotNull
import java.time.Instant

data class AdminUserRow(
    val id: Long,
    val email: String?,
    val nickname: String?,
    val storeName: String?,
    val isActive: Boolean,
    val isAdmin: Boolean,
    val subscriptionStatus: String?,
    val verificationStatus: String?,
    val createdAt: Instant?,
)

data class AdminUserPage(val rows: List<AdminUserRow>, val page: Int, val size: Int, val total: Long)

data class SetActiveRequest(
    @field:NotNull(message = "active는 필수입니다")
    val active: Boolean?,
)
```
```kotlin
package kr.ai.flori.admin.dto

import java.time.Instant

data class AdminSubscriptionRow(
    val userId: Long,
    val status: String,
    val store: String,
    val productId: String,
    val entitlement: String,
    val currentPeriodEnd: Instant?,
)
```

- [ ] **Step 2: AdminUserService (JdbcTemplate join projection + paging + active toggle)**

```kotlin
package kr.ai.flori.admin.service

import kr.ai.flori.admin.dto.AdminUserPage
import kr.ai.flori.admin.dto.AdminUserRow
import kr.ai.flori.admin.error.AdminErrorCode
import kr.ai.flori.common.error.AppException
import kr.ai.flori.user.repository.UserRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AdminUserService(
    private val jdbc: JdbcTemplate,
    private val userRepository: UserRepository,
) {
    @Transactional(readOnly = true)
    fun list(query: String?, page: Int, size: Int): AdminUserPage {
        val like = query?.takeIf { it.isNotBlank() }?.let { "%$it%" }
        val total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM users u WHERE (?::text IS NULL OR u.email ILIKE ? OR u.nickname ILIKE ?)",
            Long::class.java, like, like, like,
        ) ?: 0
        val rows = jdbc.query(
            """
            SELECT u.id, u.email, u.nickname, u.is_active, u.is_admin, u.created_at,
                   p.store_name,
                   s.status AS sub_status,
                   (SELECT bv.status FROM business_verifications bv
                      WHERE bv.user_id = u.id ORDER BY bv.created_at DESC LIMIT 1) AS verification_status
            FROM users u
            LEFT JOIN user_profiles p ON p.user_id = u.id
            LEFT JOIN subscriptions s ON s.user_id = u.id
            WHERE (?::text IS NULL OR u.email ILIKE ? OR u.nickname ILIKE ?)
            ORDER BY u.id DESC
            LIMIT ? OFFSET ?
            """.trimIndent(),
            { rs, _ ->
                AdminUserRow(
                    id = rs.getLong("id"),
                    email = rs.getString("email"),
                    nickname = rs.getString("nickname"),
                    storeName = rs.getString("store_name"),
                    isActive = rs.getBoolean("is_active"),
                    isAdmin = rs.getBoolean("is_admin"),
                    subscriptionStatus = rs.getString("sub_status"),
                    verificationStatus = rs.getString("verification_status"),
                    createdAt = rs.getTimestamp("created_at")?.toInstant(),
                )
            },
            like, like, like, size, page * size,
        )
        return AdminUserPage(rows, page, size, total)
    }

    @Transactional
    fun setActive(id: Long, active: Boolean): AdminUserRow {
        val user = userRepository.findById(id).orElseThrow { AppException(AdminErrorCode.VERIFICATION_NOT_FOUND) }
        user.isActive = active
        userRepository.save(user)
        return list(user.email, 0, 1).rows.firstOrNull { it.id == id }
            ?: AdminUserRow(id, user.email, user.nickname, null, active, user.isAdmin, null, null, null)
    }
}
```
> Note: reuse `VERIFICATION_NOT_FOUND`? No — add `USER_NOT_FOUND("E-ADM-004", NOT_FOUND, "사용자를 찾을 수 없습니다")` to `AdminErrorCode` and use it here instead.

- [ ] **Step 2b: Add `USER_NOT_FOUND` to `AdminErrorCode`** and use it in `setActive` (replace the `VERIFICATION_NOT_FOUND` above).

- [ ] **Step 3: AdminSubscriptionService**

```kotlin
package kr.ai.flori.admin.service

import kr.ai.flori.admin.dto.AdminSubscriptionRow
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AdminSubscriptionService(
    private val jdbc: JdbcTemplate,
) {
    @Transactional(readOnly = true)
    fun list(status: String?, page: Int, size: Int): List<AdminSubscriptionRow> =
        jdbc.query(
            """
            SELECT user_id, status, store, product_id, entitlement, current_period_end
            FROM subscriptions
            WHERE (?::text IS NULL OR status = ?)
            ORDER BY current_period_end DESC NULLS LAST
            LIMIT ? OFFSET ?
            """.trimIndent(),
            { rs, _ ->
                AdminSubscriptionRow(
                    userId = rs.getLong("user_id"),
                    status = rs.getString("status"),
                    store = rs.getString("store"),
                    productId = rs.getString("product_id"),
                    entitlement = rs.getString("entitlement"),
                    currentPeriodEnd = rs.getTimestamp("current_period_end")?.toInstant(),
                )
            },
            status, status, size, page * size,
        )
}
```

- [ ] **Step 4: Controllers**

```kotlin
package kr.ai.flori.admin.controller

import jakarta.validation.Valid
import kr.ai.flori.admin.dto.AdminUserPage
import kr.ai.flori.admin.dto.AdminUserRow
import kr.ai.flori.admin.dto.SetActiveRequest
import kr.ai.flori.admin.gating.RequiresAdmin
import kr.ai.flori.admin.service.AdminUserService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/users")
@RequiresAdmin
class AdminUserController(
    private val service: AdminUserService,
) {
    @GetMapping
    fun list(
        @RequestParam(required = false) query: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): AdminUserPage = service.list(query, page, size)

    @PostMapping("/{id}/active")
    fun setActive(
        @PathVariable id: Long,
        @Valid @RequestBody request: SetActiveRequest,
    ): AdminUserRow = service.setActive(id, request.active!!)
}
```
```kotlin
package kr.ai.flori.admin.controller

import kr.ai.flori.admin.dto.AdminSubscriptionRow
import kr.ai.flori.admin.gating.RequiresAdmin
import kr.ai.flori.admin.service.AdminSubscriptionService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/subscriptions")
@RequiresAdmin
class AdminSubscriptionController(
    private val service: AdminSubscriptionService,
) {
    @GetMapping
    fun list(
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): List<AdminSubscriptionRow> = service.list(status, page, size)
}
```

- [ ] **Step 5: Test (list returns self, active toggle flips is_active)**

```kotlin
package kr.ai.flori.admin

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import com.fasterxml.jackson.databind.ObjectMapper
import kr.ai.flori.admin.dto.SetActiveRequest
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.support.TestAccounts
import kr.ai.flori.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
@AutoConfigureMockMvc
class AdminUserIntegrationTest {
    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var authService: AuthService
    @Autowired private lateinit var tokenProvider: JwtTokenProvider
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var objectMapper: ObjectMapper

    @Test
    fun `운영자는 유저 목록을 조회하고 is_active 를 토글한다`() {
        val tokens = TestAccounts.register(authService, tokenProvider)
        val uid = tokenProvider.parse(tokens.accessToken)!!.userId
        val user = userRepository.findById(uid).orElseThrow()
        user.isAdmin = true
        userRepository.save(user)

        mockMvc.get("/admin/users") { header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}") }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.total") { value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)) } }

        mockMvc.post("/admin/users/$uid/active") {
            header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(SetActiveRequest(active = false))
        }.andExpect { status { isOk() } }
            .andExpect { jsonPath("$.isActive") { value(false) } }
    }
}
```

- [ ] **Step 6: Run** — `./gradlew test --tests "kr.ai.flori.admin.AdminUserIntegrationTest"` → PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/kr/ai/flori/admin src/test/kotlin/kr/ai/flori/admin/AdminUserIntegrationTest.kt
git commit -m "feat(admin): 유저 목록/상세 + is_active 토글 + 구독 목록"
```

---

## Task 5: AI health proxy — `GET /admin/health/ai`

**Files:**
- Create: `src/main/kotlin/kr/ai/flori/admin/config/AiHealthProperties.kt`
- Create: `src/main/kotlin/kr/ai/flori/admin/dto/AdminAiHealthDtos.kt`
- Create: `src/main/kotlin/kr/ai/flori/admin/service/AiHealthService.kt`
- Create: `src/main/kotlin/kr/ai/flori/admin/controller/AdminHealthController.kt`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/kotlin/kr/ai/flori/admin/AiHealthServiceTest.kt`

- [ ] **Step 1: Properties**

```kotlin
package kr.ai.flori.admin.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** AI 헬스 프록시 타깃. 미설정 시 해당 타깃은 건너뛴다(빈 문자열). */
@ConfigurationProperties(prefix = "ai.health")
data class AiHealthProperties(
    val serverUrl: String = "",
    val litellmUrl: String = "",
)
```
Register in main app class with `@ConfigurationPropertiesScan` (check `FloriApplication.kt`; add the annotation if absent).

- [ ] **Step 2: DTOs**

```kotlin
package kr.ai.flori.admin.dto

data class AiHealthTarget(val name: String, val status: String, val latencyMs: Long?, val detail: String?)
data class AiHealthResponse(val targets: List<AiHealthTarget>)
```

- [ ] **Step 3: Service (RestClient ping, short timeout, DOWN on failure)**

```kotlin
package kr.ai.flori.admin.service

import kr.ai.flori.admin.config.AiHealthProperties
import kr.ai.flori.admin.dto.AiHealthResponse
import kr.ai.flori.admin.dto.AiHealthTarget
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

/** ai-server/litellm 헬스 프록시. 상태/지연만 노출(키·내부 host 비노출). */
@Service
class AiHealthService(
    private val properties: AiHealthProperties,
) {
    private val restClient: RestClient =
        RestClient.builder()
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(2000)
                    setReadTimeout(3000)
                },
            ).build()

    fun check(): AiHealthResponse {
        val targets = buildList {
            if (properties.serverUrl.isNotBlank()) add(ping("ai-server", properties.serverUrl))
            if (properties.litellmUrl.isNotBlank()) add(ping("litellm", properties.litellmUrl))
        }
        return AiHealthResponse(targets)
    }

    private fun ping(name: String, url: String): AiHealthTarget {
        val start = System.nanoTime()
        return try {
            restClient.get().uri(url).retrieve().toBodilessEntity()
            AiHealthTarget(name, "UP", elapsedMs(start), null)
        } catch (e: Exception) {
            AiHealthTarget(name, "DOWN", elapsedMs(start), e.message?.take(120))
        }
    }

    private fun elapsedMs(start: Long): Long = (System.nanoTime() - start) / 1_000_000
}
```

- [ ] **Step 4: Controller**

```kotlin
package kr.ai.flori.admin.controller

import kr.ai.flori.admin.dto.AiHealthResponse
import kr.ai.flori.admin.gating.RequiresAdmin
import kr.ai.flori.admin.service.AiHealthService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/health")
@RequiresAdmin
class AdminHealthController(
    private val service: AiHealthService,
) {
    @GetMapping("/ai")
    fun ai(): AiHealthResponse = service.check()
}
```

- [ ] **Step 5: application.yml** — add near other config:
```yaml
ai:
  health:
    server-url: ${AI_HEALTH_SERVER_URL:}
    litellm-url: ${AI_HEALTH_LITELLM_URL:}
```

- [ ] **Step 6: Unit test (no network — empty config yields empty targets)**

```kotlin
package kr.ai.flori.admin

import kr.ai.flori.admin.config.AiHealthProperties
import kr.ai.flori.admin.service.AiHealthService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class AiHealthServiceTest {
    @Test
    fun `타깃 미설정이면 빈 결과`() {
        val service = AiHealthService(AiHealthProperties())
        assertTrue(service.check().targets.isEmpty())
    }

    @Test
    fun `도달 불가 host 는 DOWN 으로 degrade`() {
        val service = AiHealthService(AiHealthProperties(serverUrl = "http://127.0.0.1:1/health"))
        val target = service.check().targets.single()
        assertEquals("DOWN", target.status)
    }
}
```

- [ ] **Step 7: Run** — `./gradlew test --tests "kr.ai.flori.admin.AiHealthServiceTest"` → PASS

- [ ] **Step 8: Commit**

```bash
git add src/main/kotlin/kr/ai/flori/admin src/main/resources/application.yml src/test/kotlin/kr/ai/flori/admin/AiHealthServiceTest.kt
git commit -m "feat(admin): AI 헬스 프록시(ai-server/litellm)"
```

---

## Task 6: Full suite + lint

- [ ] **Step 1:** `./gradlew ktlintCheck` → fix any violations.
- [ ] **Step 2:** `./gradlew test` → all green.
- [ ] **Step 3: Commit any lint fixes**
```bash
git add -p
git commit -m "chore(admin): ktlint 정리"
```

---

## Self-Review Notes (author)

- **Spec coverage:** stats(Task2), verification approve/reject(Task3), user browse + is_active(Task4), subscriptions(Task4), AI health(Task5), gate(Task1) — all covered.
- **Type consistency:** `AdminErrorCode` gains `USER_NOT_FOUND` in Task 4 Step 2b — make sure it's added before `AdminUserService` uses it. Verification statuses bound via `BusinessVerificationStatuses` enum in `@RequestParam` (Spring auto-converts).
- **Bootstrap (out of plan, run at deploy):** `UPDATE users SET is_admin = true WHERE email = 'hchsa77@gmail.com';` — requires user approval before executing against the DB.
- **Open check during impl:** confirm `FloriApplication` has `@ConfigurationPropertiesScan` (Task 5 Step 1); confirm `GlobalExceptionHandler` maps `AppException` HttpStatus from `ErrorCode.status` (it does for existing domains).
