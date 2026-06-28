# 사업자 인증 알림톡(접수·승인·거절) + 발송 가시화 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 점주의 사업자 인증 제출·승인·거절 시 카카오 알림톡(솔라피)을 발송하고, 세 발송 결과를 운영 콘솔 발송 로그에 기록한다.

**Architecture:** 기존 패턴(도메인 이벤트 → `@TransactionalEventListener(AFTER_COMMIT)` → `@Async` `SolapiNotifier` best-effort)을 그대로 확장한다. 발송 결과 기록은 `common`에 포트 인터페이스(`NotificationSendRecorder`)를 두고 `admin`이 어댑터로 구현해 `common → admin` 역의존을 피한다. 신규 인프라 없음.

**Tech Stack:** Kotlin 2.1 / Spring Boot 3.5 / Java 21 / Gradle. 테스트는 Zonky embedded PostgreSQL + JUnit5 + `@MockitoBean`(plain Mockito) + AssertJ.

## Global Constraints

- 시크릿/실값(API Key·Secret·발신번호·pfId·templateId)은 코드/문서/깃에 금지 — `application.yml`은 `${ENV}` 참조만.
- 멀티테넌시: 신원은 토큰/이벤트에서만 도출. 발송 로그엔 `targetUserId`만 기록(전화번호 평문 저장 금지).
- 전화번호 로그 마스킹(`maskPhone`, 뒤 4자리) 유지.
- 알림톡 변수: 접수/승인 = `#{상호}`, 거절 = `#{상호}` + `#{사유}`.
- 발송 로그 규약: `source="alimtalk"`(채널), `type="business_verification"`(도메인), 성공 `sentCount=1/failedCount=0`, 실패 `sentCount=0/failedCount=1`. (source CHECK에 'alimtalk' 추가 마이그레이션 필요 — Task 1)
- best-effort: 발송/기록 실패가 본 트랜잭션(제출/승인/거절)을 막지 않는다.
- 커밋: `git add` 변경 파일만, 한국어 conventional commit, `Co-Authored-By: Claude <noreply@anthropic.com>`.
- 각 태스크 종료 시 `./gradlew ktlintFormat` 후 해당 테스트 통과 확인.

---

### Task 1: 발송 기록 포트 + admin 어댑터

`SolapiNotifier`(common)가 `NotificationSendLog`(admin)에 직접 의존하지 않도록, common에 포트 인터페이스를 정의하고 admin이 구현한다.

**Files:**
- Create: `src/main/kotlin/kr/ai/flori/common/notification/NotificationSendRecorder.kt`
- Create: `src/main/kotlin/kr/ai/flori/admin/service/NotificationSendLogRecorderAdapter.kt`
- Test: `src/test/kotlin/kr/ai/flori/admin/service/NotificationSendLogRecorderAdapterTest.kt`

**Interfaces:**
- Produces: `interface NotificationSendRecorder { fun record(source: String, type: String, success: Boolean, targetUserId: Long?, title: String?, errorMessage: String?) }`
- Consumes: 기존 `NotificationSendLogService.record(source, type, sentCount, failedCount, title?, body?, segment?, targetUserId?, broadcastId?, actorUserId?, errorMessage?)`, 기존 `NotificationSendLogRepository: JpaRepository<NotificationSendLog, Long>`.

- [ ] **Step 1: 포트 인터페이스 작성**

`src/main/kotlin/kr/ai/flori/common/notification/NotificationSendRecorder.kt`
```kotlin
package kr.ai.flori.common.notification

/**
 * 발송 결과 기록 포트. common(예: SolapiNotifier)이 admin의 발송 로그에 직접 의존하지 않도록
 * 인터페이스만 common에 두고, 구현(어댑터)은 admin에 둔다(의존성 방향 보호).
 */
interface NotificationSendRecorder {
    fun record(
        source: String,
        type: String,
        success: Boolean,
        targetUserId: Long?,
        title: String?,
        errorMessage: String?,
    )
}
```

- [ ] **Step 2: 어댑터 구현 작성**

`src/main/kotlin/kr/ai/flori/admin/service/NotificationSendLogRecorderAdapter.kt`
```kotlin
package kr.ai.flori.admin.service

import kr.ai.flori.common.notification.NotificationSendRecorder
import org.springframework.stereotype.Component

/** [NotificationSendRecorder]를 운영 콘솔 발송 로그(notification_send_logs)에 위임하는 어댑터. */
@Component
class NotificationSendLogRecorderAdapter(
    private val logService: NotificationSendLogService,
) : NotificationSendRecorder {
    override fun record(
        source: String,
        type: String,
        success: Boolean,
        targetUserId: Long?,
        title: String?,
        errorMessage: String?,
    ) {
        logService.record(
            source = source,
            type = type,
            sentCount = if (success) 1 else 0,
            failedCount = if (success) 0 else 1,
            title = title,
            targetUserId = targetUserId,
            errorMessage = errorMessage,
        )
    }
}
```

- [ ] **Step 3: 실패하는 테스트 작성**

`src/test/kotlin/kr/ai/flori/admin/service/NotificationSendLogRecorderAdapterTest.kt`
```kotlin
package kr.ai.flori.admin.service

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.admin.repository.NotificationSendLogRepository
import kr.ai.flori.common.notification.NotificationSendRecorder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
class NotificationSendLogRecorderAdapterTest {
    @Autowired private lateinit var recorder: NotificationSendRecorder

    @Autowired private lateinit var repository: NotificationSendLogRepository

    @Test
    fun `성공 기록은 sent로, 실패 기록은 failed로 적재된다`() {
        recorder.record("alimtalk", "business_verification", true, 1L, "사업자 인증 승인", null)
        recorder.record("alimtalk", "business_verification", false, 2L, "사업자 인증 접수", "boom")

        val all = repository.findAll().filter { it.source == "alimtalk" }
        assertThat(all).hasSize(2)
        val ok = all.first { it.targetUserId == 1L }
        assertThat(ok.status).isEqualTo("sent")
        assertThat(ok.sentCount).isEqualTo(1)
        val failed = all.first { it.targetUserId == 2L }
        assertThat(failed.status).isEqualTo("failed")
        assertThat(failed.errorMessage).isEqualTo("boom")
    }
}
```

- [ ] **Step 4: 테스트 실패 확인**

Run: `./gradlew test --tests "kr.ai.flori.admin.service.NotificationSendLogRecorderAdapterTest"`
Expected: 컴파일 실패 또는 빈(`NotificationSendRecorder`) 미존재 → FAIL (Step 1–2 미작성 시). Step 1–2 작성 후엔 PASS.

- [ ] **Step 5: 포맷 + 테스트 통과 확인**

Run: `./gradlew ktlintFormat test --tests "kr.ai.flori.admin.service.NotificationSendLogRecorderAdapterTest"`
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add src/main/kotlin/kr/ai/flori/common/notification/NotificationSendRecorder.kt \
        src/main/kotlin/kr/ai/flori/admin/service/NotificationSendLogRecorderAdapter.kt \
        src/test/kotlin/kr/ai/flori/admin/service/NotificationSendLogRecorderAdapterTest.kt
git commit -m "feat(admin): 발송 기록 포트(NotificationSendRecorder)+어댑터 — common→admin 역의존 차단

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: SolapiProperties 확장 + SolapiNotifier 리팩터(공통 헬퍼·기록·userId)

승인 발송을 공통 헬퍼로 리팩터해 기록을 일원화하고, 자격증명 검사와 템플릿ID 검사를 분리한다(템플릿별 검수 시점이 달라 일부만 비어도 나머지는 발송 가능해야 함).

**Files:**
- Modify: `src/main/kotlin/kr/ai/flori/common/notification/solapi/SolapiProperties.kt`
- Modify: `src/main/resources/application.yml:48-53`
- Modify: `src/main/kotlin/kr/ai/flori/common/notification/solapi/SolapiNotifier.kt`
- Modify: `src/main/kotlin/kr/ai/flori/admin/listener/BusinessVerificationReviewedListener.kt:42-45` (호출 시그니처에 userId 추가)
- Test: `src/test/kotlin/kr/ai/flori/common/notification/solapi/SolapiNotifierTest.kt`

**Interfaces:**
- Produces:
  - `SolapiProperties.submittedTemplateId: String`, `SolapiProperties.rejectedTemplateId: String`, `SolapiProperties.hasCredentials(): Boolean`
  - `SolapiNotifier.sendBusinessApproved(userId: Long, phoneNumber: String, storeName: String)` (시그니처 변경: userId 추가)
- Consumes: `NotificationSendRecorder`(Task 1).

- [ ] **Step 1: SolapiProperties 확장**

`SolapiProperties.kt` — 필드 2개 추가 + `isConfigured()`를 자격증명 전용 `hasCredentials()`로 교체:
```kotlin
@ConfigurationProperties(prefix = "solapi")
data class SolapiProperties(
    val apiKey: String = "",
    val apiSecret: String = "",
    /** 등록된 SMS 발신번호 — 알림톡 실패 시 폴백 + 발신자 표기. */
    val senderPhone: String = "",
    /** 카카오 알림톡 발신프로필 ID (비즈채널 인증 후 발급). */
    val pfId: String = "",
    /** 사업자 인증 승인 알림톡 템플릿 ID. */
    val approvalTemplateId: String = "",
    /** 사업자 인증 접수(제출) 알림톡 템플릿 ID. */
    val submittedTemplateId: String = "",
    /** 사업자 인증 거절(반려) 알림톡 템플릿 ID. */
    val rejectedTemplateId: String = "",
    val baseUrl: String = "https://api.solapi.com",
) {
    /** 발송 공통 자격(키/시크릿/발신프로필/발신번호)이 모두 설정됐는지. 템플릿ID는 발송별로 따로 검사. */
    fun hasCredentials(): Boolean =
        apiKey.isNotBlank() &&
            apiSecret.isNotBlank() &&
            pfId.isNotBlank() &&
            senderPhone.isNotBlank()
}
```

- [ ] **Step 2: application.yml 템플릿 env 2개 추가**

`src/main/resources/application.yml`의 solapi 블록(48–53행)을 아래로 교체:
```yaml
solapi:
  api-key: ${SOLAPI_API_KEY:}
  api-secret: ${SOLAPI_API_SECRET:}
  sender-phone: ${SOLAPI_SENDER_PHONE:}                              # 등록된 SMS 발신번호(폴백)
  pf-id: ${SOLAPI_PF_ID:}                                            # 카카오 발신프로필 ID
  approval-template-id: ${SOLAPI_APPROVAL_TEMPLATE_ID:}              # 사업자 인증 승인 알림톡 템플릿
  submitted-template-id: ${SOLAPI_SUBMITTED_TEMPLATE_ID:}            # 사업자 인증 접수 알림톡 템플릿
  rejected-template-id: ${SOLAPI_REJECTED_TEMPLATE_ID:}              # 사업자 인증 거절 알림톡 템플릿
```

- [ ] **Step 3: SolapiNotifier 리팩터 — 공통 헬퍼 + recorder + 승인 시그니처에 userId**

`SolapiNotifier.kt` 전체를 아래로 교체:
```kotlin
package kr.ai.flori.common.notification.solapi

import kr.ai.flori.common.notification.NotificationSendRecorder
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * SOLAPI 알림톡 발송 도구(재사용). 카카오 알림톡은 공식 중계사(SOLAPI) REST(/messages/v4/send)를
 * HMAC-SHA256 인증으로 호출한다. @Async(비차단) + best-effort(실패는 로깅만, 본 작업 비차단).
 * 미설정(자격/템플릿/발신번호 공백)이면 콘솔 폴백. 발송 시도분은 [NotificationSendRecorder]로 기록.
 */
@Component
class SolapiNotifier(
    private val properties: SolapiProperties,
    private val recorder: NotificationSendRecorder,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val restClient =
        RestClient
            .builder()
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(CONNECT_TIMEOUT)
                    setReadTimeout(READ_TIMEOUT)
                },
            ).build()

    /** 사업자 인증 접수(제출) → 점주에게 접수 알림톡. */
    @Async
    fun sendBusinessSubmitted(
        userId: Long,
        phoneNumber: String,
        storeName: String,
    ) = send(userId, phoneNumber, properties.submittedTemplateId, mapOf("#{상호}" to storeName), TITLE_SUBMITTED)

    /** 사업자 인증 승인 → 점주에게 승인 알림톡. */
    @Async
    fun sendBusinessApproved(
        userId: Long,
        phoneNumber: String,
        storeName: String,
    ) = send(userId, phoneNumber, properties.approvalTemplateId, mapOf("#{상호}" to storeName), TITLE_APPROVED)

    /** 사업자 인증 거절 → 점주에게 사유 포함 알림톡. */
    @Async
    fun sendBusinessRejected(
        userId: Long,
        phoneNumber: String,
        storeName: String,
        reason: String,
    ) = send(
        userId,
        phoneNumber,
        properties.rejectedTemplateId,
        mapOf("#{상호}" to storeName, "#{사유}" to reason),
        TITLE_REJECTED,
    )

    @Suppress("TooGenericExceptionCaught")
    private fun send(
        userId: Long,
        phoneNumber: String,
        templateId: String,
        variables: Map<String, String>,
        title: String,
    ) {
        val to = phoneNumber.filter { it.isDigit() }
        if (to.isBlank()) {
            log.warn("[Solapi] 발송 스킵 — 전화번호 없음(userProfile.phoneNumber 미백필?) title={}", title)
            return
        }
        if (!properties.hasCredentials() || templateId.isBlank()) {
            log.info("[Solapi] 미설정 — 콘솔 폴백: {} to={} vars={}", title, to, variables)
            return
        }
        try {
            postAlimtalk(to, templateId, variables)
            recorder.record(SOURCE, TYPE, success = true, targetUserId = userId, title = title, errorMessage = null)
            log.info("[Solapi] {} 발송 to={}", title, maskPhone(to))
        } catch (e: Exception) {
            recorder.record(SOURCE, TYPE, success = false, targetUserId = userId, title = title, errorMessage = e.message)
            log.warn("[Solapi] 발송 실패(무시): {} {}", title, e.message)
        }
    }

    private fun postAlimtalk(
        to: String,
        templateId: String,
        variables: Map<String, String>,
    ) {
        val date = Instant.now().toString()
        val salt = UUID.randomUUID().toString().replace("-", "")
        val signature = hmacSha256Hex(properties.apiSecret, date + salt)
        val auth = "HMAC-SHA256 apiKey=${properties.apiKey}, date=$date, salt=$salt, signature=$signature"
        val body =
            mapOf(
                "message" to
                    mapOf(
                        "to" to to,
                        "from" to properties.senderPhone.filter { it.isDigit() },
                        "kakaoOptions" to
                            mapOf(
                                "pfId" to properties.pfId,
                                "templateId" to templateId,
                                "variables" to variables,
                                "disableSms" to false,
                            ),
                    ),
            )
        restClient
            .post()
            .uri("${properties.baseUrl}/messages/v4/send")
            .header("Authorization", auth)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .toBodilessEntity()
    }

    private fun hmacSha256Hex(
        secret: String,
        data: String,
    ): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun maskPhone(phone: String): String =
        if (phone.length >= PHONE_VISIBLE_TAIL) {
            "*".repeat(phone.length - PHONE_VISIBLE_TAIL) + phone.takeLast(PHONE_VISIBLE_TAIL)
        } else {
            "****"
        }

    private companion object {
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(5)
        val READ_TIMEOUT: Duration = Duration.ofSeconds(10)
        const val PHONE_VISIBLE_TAIL = 4
        const val SOURCE = "alimtalk"
        const val TYPE = "business_verification"
        const val TITLE_SUBMITTED = "사업자 인증 접수"
        const val TITLE_APPROVED = "사업자 인증 승인"
        const val TITLE_REJECTED = "사업자 인증 거절"
    }
}
```

- [ ] **Step 4: 승인 리스너의 호출부 userId 반영**

`BusinessVerificationReviewedListener.kt`의 승인 분기(42–45행)를 아래로 교체:
```kotlin
        // 승인 시에만 점주에게 알림톡 통보(거절은 Task 4에서 추가). 전화번호는 프로필에서 조회.
        if (event.approved) {
            val phone = userProfileRepository.findById(event.userId).map { it.phoneNumber }.orElse("")
            solapiNotifier.sendBusinessApproved(event.userId, phone, event.businessName)
        }
```

- [ ] **Step 5: 실패-기록 경로 테스트 작성**

자격은 설정하되 baseUrl을 비라우팅 호스트로 두어 발송이 반드시 실패 → recorder가 실제 `failed` 행을 적재하는지 검증(@Async라 폴링).

`src/test/kotlin/kr/ai/flori/common/notification/solapi/SolapiNotifierTest.kt`
```kotlin
package kr.ai.flori.common.notification.solapi

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.admin.entity.NotificationSendLog
import kr.ai.flori.admin.repository.NotificationSendLogRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * 자격은 설정하되 base-url을 연결 거부되는 호스트로 두어 발송을 강제 실패시키고,
 * recorder가 failed 행을 적재하는지 검증. (@Async라 폴링으로 대기)
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest(
    properties = [
        "solapi.api-key=test-key",
        "solapi.api-secret=test-secret",
        "solapi.sender-phone=01000000000",
        "solapi.pf-id=KA01PFtest",
        "solapi.approval-template-id=KA01TPtest",
        "solapi.base-url=http://localhost:1",
    ],
)
class SolapiNotifierTest {
    @Autowired private lateinit var notifier: SolapiNotifier

    @Autowired private lateinit var repository: NotificationSendLogRepository

    private fun awaitLog(): NotificationSendLog? {
        val deadline = System.currentTimeMillis() + 4000
        while (System.currentTimeMillis() < deadline) {
            val row = repository.findAll().firstOrNull { it.source == "alimtalk" }
            if (row != null) return row
            Thread.sleep(50)
        }
        return null
    }

    @Test
    fun `발송 실패 시 failed 로그가 적재된다`() {
        notifier.sendBusinessApproved(7L, "01012345678", "플로리 꽃집")

        val row = awaitLog()
        assertThat(row).isNotNull
        assertThat(row!!.type).isEqualTo("business_verification")
        assertThat(row.status).isEqualTo("failed")
        assertThat(row.targetUserId).isEqualTo(7L)
        assertThat(row.title).isEqualTo("사업자 인증 승인")
    }

    @Test
    fun `전화번호가 없으면 발송도 기록도 하지 않는다`() {
        notifier.sendBusinessApproved(8L, "", "플로리 꽃집")

        Thread.sleep(500)
        assertThat(repository.findAll().filter { it.targetUserId == 8L }).isEmpty()
    }
}
```

- [ ] **Step 6: 테스트 실패 확인 → 구현 후 통과 확인**

Run: `./gradlew ktlintFormat test --tests "kr.ai.flori.common.notification.solapi.SolapiNotifierTest" --tests "kr.ai.flori.admin.AdminVerificationIntegrationTest"`
Expected: 신규 `SolapiNotifierTest` PASS, 기존 `AdminVerificationIntegrationTest`(미설정 → skip 경로) 회귀 PASS.

- [ ] **Step 7: 커밋**

```bash
git add src/main/kotlin/kr/ai/flori/common/notification/solapi/SolapiProperties.kt \
        src/main/resources/application.yml \
        src/main/kotlin/kr/ai/flori/common/notification/solapi/SolapiNotifier.kt \
        src/main/kotlin/kr/ai/flori/admin/listener/BusinessVerificationReviewedListener.kt \
        src/test/kotlin/kr/ai/flori/common/notification/solapi/SolapiNotifierTest.kt
git commit -m "refactor(api): SolapiNotifier 공통 헬퍼+발송기록+템플릿별 검사 — 접수/거절 템플릿 필드 추가

- SolapiProperties: submitted/rejected templateId 추가, isConfigured→hasCredentials(자격 전용)
- 발송 시도분을 NotificationSendRecorder로 기록(source=alimtalk,type=business_verification)
- sendBusinessApproved 시그니처에 userId 추가(기록용)

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: 접수 알림톡 — 제출 리스너 배선

제출 이벤트 리스너가 Discord 알림 뒤 점주에게 접수 알림톡을 발송한다.

**Files:**
- Modify: `src/main/kotlin/kr/ai/flori/verification/listener/BusinessVerificationEventListener.kt`
- Test: `src/test/kotlin/kr/ai/flori/verification/listener/BusinessVerificationSubmitNotifyTest.kt`

**Interfaces:**
- Consumes: `SolapiNotifier.sendBusinessSubmitted(userId, phoneNumber, storeName)`(Task 2), `UserProfileRepository.findById`, 기존 `BusinessVerificationSubmittedEvent(userId, businessName, businessNumber, representativeName, businessLicenseUrl)`.

- [ ] **Step 1: 실패하는 배선 테스트 작성**

`SolapiNotifier`를 `@MockitoBean`으로 대체하고, 제출 시 `sendBusinessSubmitted`가 상호와 함께 호출되는지 검증.

`src/test/kotlin/kr/ai/flori/verification/listener/BusinessVerificationSubmitNotifyTest.kt`
```kotlin
package kr.ai.flori.verification.listener

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.notification.solapi.SolapiNotifier
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.common.tenant.TenantContext
import kr.ai.flori.support.TestAccounts
import kr.ai.flori.user.repository.UserRepository
import kr.ai.flori.verification.dto.BusinessVerificationSubmitRequest
import kr.ai.flori.verification.service.BusinessVerificationService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest(properties = ["aws.cloudfront.domain=cdn.example.com"])
class BusinessVerificationSubmitNotifyTest {
    @Autowired private lateinit var service: BusinessVerificationService

    @Autowired private lateinit var authService: AuthService

    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    @Autowired private lateinit var userRepository: UserRepository

    @MockitoBean private lateinit var solapiNotifier: SolapiNotifier

    @AfterEach
    fun tearDown() = TenantContext.clear()

    // 제네릭 T 경유로 null을 비-널 Kotlin 파라미터에 안전 주입(mockito-kotlin 미사용 패턴).
    @Suppress("UNCHECKED_CAST")
    private fun <T> uninitialized(): T = null as T

    private fun captureStore(captor: ArgumentCaptor<String>): String {
        captor.capture()
        return uninitialized()
    }

    @Test
    fun `제출하면 접수 알림톡이 상호와 함께 발송된다`() {
        val email = "submit-notify@flori.dev"
        TestAccounts.register(authService, tokenProvider, email)
        val userId = requireNotNull(userRepository.findByEmail(email)).id!!
        TenantContext.set(userId)

        service.submit(
            BusinessVerificationSubmitRequest(
                businessNumber = "1234567890",
                businessName = "플로리 꽃집",
                representativeName = "홍길동",
                businessLicenseUrl = "https://cdn.example.com/business-licenses/$userId/a.jpg",
            ),
        )

        val storeCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(solapiNotifier).sendBusinessSubmitted(anyLong(), anyString(), captureStore(storeCaptor))
        assertThat(storeCaptor.value).isEqualTo("플로리 꽃집")
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "kr.ai.flori.verification.listener.BusinessVerificationSubmitNotifyTest"`
Expected: FAIL — 리스너가 아직 `sendBusinessSubmitted`를 호출하지 않음(`Wanted but not invoked`).

- [ ] **Step 3: 제출 리스너에 알림톡 발송 추가**

`BusinessVerificationEventListener.kt` 전체를 아래로 교체:
```kotlin
package kr.ai.flori.verification.listener

import kr.ai.flori.common.notification.discord.DiscordChannel
import kr.ai.flori.common.notification.discord.DiscordMessage
import kr.ai.flori.common.notification.discord.DiscordNotifier
import kr.ai.flori.common.notification.solapi.SolapiNotifier
import kr.ai.flori.common.util.KST
import kr.ai.flori.user.repository.UserProfileRepository
import kr.ai.flori.verification.event.BusinessVerificationSubmittedEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * 사업자 인증 신청 → ① 운영자 Discord 알림 ② 점주에게 접수 알림톡(SOLAPI).
 * DB 커밋 후(AFTER_COMMIT) 비동기 발송(각 Notifier @Async).
 */
@Component
class BusinessVerificationEventListener(
    private val discordNotifier: DiscordNotifier,
    private val solapiNotifier: SolapiNotifier,
    private val userProfileRepository: UserProfileRepository,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: BusinessVerificationSubmittedEvent) {
        val now = ZonedDateTime.now(KST).format(TIMESTAMP_FORMAT)
        val message =
            """
            **[사업자 인증 신청 📄]**
            - 신청 일자: $now
            - userId: ${event.userId}
            - 상호: ${event.businessName}
            - 사업자번호: ${event.businessNumber}
            - 대표자명: ${event.representativeName}
            - 등록증: ${event.businessLicenseUrl}
            """.trimIndent()
        discordNotifier.notify(DiscordChannel.VERIFICATION, DiscordMessage.of(message))

        val phone = userProfileRepository.findById(event.userId).map { it.phoneNumber }.orElse("")
        solapiNotifier.sendBusinessSubmitted(event.userId, phone, event.businessName)
    }

    private companion object {
        val TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
```

- [ ] **Step 4: 포맷 + 테스트 통과 확인**

Run: `./gradlew ktlintFormat test --tests "kr.ai.flori.verification.listener.BusinessVerificationSubmitNotifyTest" --tests "kr.ai.flori.verification.service.BusinessVerificationServiceIntegrationTest"`
Expected: 신규 PASS, 기존 제출 통합테스트 회귀 PASS.

- [ ] **Step 5: 커밋**

```bash
git add src/main/kotlin/kr/ai/flori/verification/listener/BusinessVerificationEventListener.kt \
        src/test/kotlin/kr/ai/flori/verification/listener/BusinessVerificationSubmitNotifyTest.kt
git commit -m "feat(verification): 사업자 인증 제출 시 접수 알림톡 발송(SOLAPI)

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: 거절 알림톡 — 심사 리스너 거절 분기

승인 리스너의 거절 분기에 사유 포함 알림톡 발송을 추가한다(현재 거절은 Discord만).

**Files:**
- Modify: `src/main/kotlin/kr/ai/flori/admin/listener/BusinessVerificationReviewedListener.kt`
- Test: `src/test/kotlin/kr/ai/flori/admin/BusinessVerificationReviewNotifyTest.kt`

**Interfaces:**
- Consumes: `SolapiNotifier.sendBusinessRejected(userId, phoneNumber, storeName, reason)`(Task 2), 기존 `BusinessVerificationReviewedEvent(userId, businessName, approved, reason)`.

- [ ] **Step 1: 실패하는 분기 테스트 작성**

`SolapiNotifier`를 `@MockitoBean`으로 대체하고, 거절 시 `sendBusinessRejected`가 사유와 함께 호출 + 승인 시 미호출, 승인 시 `sendBusinessApproved` 호출 + 거절 미호출 검증.

`src/test/kotlin/kr/ai/flori/admin/BusinessVerificationReviewNotifyTest.kt`
```kotlin
package kr.ai.flori.admin

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import kr.ai.flori.auth.service.AuthService
import kr.ai.flori.common.notification.solapi.SolapiNotifier
import kr.ai.flori.common.security.JwtTokenProvider
import kr.ai.flori.support.TestAccounts
import kr.ai.flori.user.repository.UserRepository
import kr.ai.flori.verification.entity.BusinessVerification
import kr.ai.flori.verification.repository.BusinessVerificationRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.context.bean.override.mockito.MockitoBean

@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
@AutoConfigureMockMvc
class BusinessVerificationReviewNotifyTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var authService: AuthService

    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var verificationRepository: BusinessVerificationRepository

    @MockitoBean private lateinit var solapiNotifier: SolapiNotifier

    @Suppress("UNCHECKED_CAST")
    private fun <T> uninitialized(): T = null as T

    private fun captureStr(captor: ArgumentCaptor<String>): String {
        captor.capture()
        return uninitialized()
    }

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
        return verificationRepository
            .save(
                BusinessVerification(
                    userId = uid,
                    businessNumber = "1234567890",
                    businessName = "플로리",
                    representativeName = "홍길동",
                    businessLicenseUrl = "https://cdn.example.com/business-licenses/$uid/a.jpg",
                ),
            ).id!!
    }

    @Test
    fun `거절하면 사유 포함 거절 알림톡이 발송된다`() {
        val token = adminToken()
        val id = pendingFor()

        mockMvc.post("/admin/verifications/$id/reject") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """{"reason":"등록증 사진이 흐립니다"}"""
        }.andExpect { status { isOk() } }

        val reasonCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(solapiNotifier).sendBusinessRejected(anyLong(), anyString(), anyString(), captureStr(reasonCaptor))
        assertThat(reasonCaptor.value).isEqualTo("등록증 사진이 흐립니다")
        Mockito.verify(solapiNotifier, Mockito.never()).sendBusinessApproved(anyLong(), anyString(), anyString())
    }

    @Test
    fun `승인하면 승인 알림톡만 발송되고 거절 알림톡은 없다`() {
        val token = adminToken()
        val id = pendingFor()

        mockMvc.post("/admin/verifications/$id/approve") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect { status { isOk() } }

        Mockito.verify(solapiNotifier).sendBusinessApproved(anyLong(), anyString(), anyString())
        Mockito.verify(solapiNotifier, Mockito.never())
            .sendBusinessRejected(anyLong(), anyString(), anyString(), anyString())
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "kr.ai.flori.admin.BusinessVerificationReviewNotifyTest"`
Expected: `거절하면…` FAIL(거절 알림톡 미호출), `승인하면…`은 PASS일 수 있음.

- [ ] **Step 3: 리스너 거절 분기 추가**

`BusinessVerificationReviewedListener.kt`의 승인/거절 분기(현재 승인만)를 아래로 교체:
```kotlin
        // 점주에게 결과 알림톡(전화번호는 프로필에서 조회). 승인=완료 안내, 거절=사유 안내.
        val phone = userProfileRepository.findById(event.userId).map { it.phoneNumber }.orElse("")
        if (event.approved) {
            solapiNotifier.sendBusinessApproved(event.userId, phone, event.businessName)
        } else {
            solapiNotifier.sendBusinessRejected(event.userId, phone, event.businessName, event.reason.orEmpty())
        }
```

- [ ] **Step 4: 포맷 + 테스트 통과 확인**

Run: `./gradlew ktlintFormat test --tests "kr.ai.flori.admin.BusinessVerificationReviewNotifyTest" --tests "kr.ai.flori.admin.AdminVerificationIntegrationTest"`
Expected: 신규 2건 PASS, 기존 회귀 PASS.

- [ ] **Step 5: 커밋**

```bash
git add src/main/kotlin/kr/ai/flori/admin/listener/BusinessVerificationReviewedListener.kt \
        src/test/kotlin/kr/ai/flori/admin/BusinessVerificationReviewNotifyTest.kt
git commit -m "feat(admin): 사업자 인증 거절 시 사유 포함 알림톡 발송(SOLAPI)

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: 전체 게이트 + 문서 동기화

**Files:**
- Modify: `CLAUDE.md`(verification 도메인 한 줄에 알림톡 3종/발송로그 기재). `docs/DATABASE.md`에 notification_send_logs.source CHECK 'alimtalk' 추가 반영(Task 1 마이그레이션).
- Verify: 전체 빌드/품질 게이트.

- [ ] **Step 1: 전체 게이트 실행**

Run: `./gradlew build test`
Expected: ktlint + detekt + 전체 테스트 + JaCoCo 80% 게이트 PASS. (실패 시 해당 태스크로 복귀)

- [ ] **Step 2: detekt 함수 수/복잡도 경고 확인**

Run: `./gradlew detekt`
Expected: PASS. (SolapiNotifier 함수 증가로 `TooManyFunctions` 임계 위반 시, `postAlimtalk`/`hmacSha256Hex`를 그대로 유지하되 신규 위반 없을 것으로 예상. 위반 시 비공개 헬퍼를 합치지 말고 임계 내 유지 방안만 적용)

- [ ] **Step 3: CLAUDE.md 도메인 설명 갱신**

`CLAUDE.md`의 프로젝트 구조에서 `verification/` 줄을 갱신:
```
├── verification/          # 사업자 인증 (신청·상태조회·presigned 업로드·게이팅) + 결과 알림톡(접수/승인/거절, SOLAPI) — 발송 결과는 notification_send_logs(source=alimtalk, type=business_verification)
```

- [ ] **Step 4: 커밋**

```bash
git add CLAUDE.md
git commit -m "docs(verification): CLAUDE.md에 사업자 인증 알림톡 3종/발송로그 반영

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 6 (web): 발송 로그 가독성 — 한글 칩 전면화

요청: 필터 드롭다운·표 셀의 raw 영어를 전부 한글 칩으로. 작업 위치는 **web 워크트리** `web-session2-biz-approval-notify`. 검증은 `npm run lint && npm run build` + 화면 육안 확인(컨트롤러/사용자). 기존 `StatusBadge`(`components/console/status-badge.tsx`) 톤 칩 재사용.

**Modify:** `src/app/(console)/console/notification-logs/notification-logs-client.tsx`

- [ ] **타입(type) 한글화**: `TYPE_OPTIONS` 라벨을 한글로(`broadcast→마케팅/캠페인`, `reservation_reminder→예약 리마인더`, `notice→공지`) + `{ value: 'business_verification', label: '사업자 인증' }` 추가. `TYPE_META`에 `business_verification: { tone: 'info', label: '사업자 인증' }` 추가. (표 셀은 이미 `TypeBadge` 칩)
- [ ] **소스(source) 칩화**: `SOURCE_OPTIONS` 라벨 한글로(`web→웹`, `cron→스케줄러`, `system→시스템`) + `{ value: 'alimtalk', label: '알림톡' }` 추가. `SOURCE_META: Record<string,{tone,label}>` 신설(`web:{muted,웹}`, `cron:{muted,스케줄러}`, `system:{muted,시스템}`, `alimtalk:{info,알림톡}`) + `SourceBadge` 컴포넌트. 표 본문 `소스` 셀(현재 raw `{log.source}`)을 `<SourceBadge source={log.source} />`로 교체.
- [ ] **상태(status) 칩화**: `STATUS_OPTIONS` 라벨 한글로(`sent→성공`, `failed→실패`, `partial→부분 성공`). 표 첫 칼럼의 색 점(STATUS_DOT)을 `StatusBadge` 칩(성공=success, 실패=danger, 부분=warning, 한글 라벨)으로 교체. 헤더에 빈 `<TableHead className="w-8" />` → `<TableHead>상태</TableHead>`로.
- [ ] `npm run lint && npm run build` 통과 + 화면 확인. 커밋(web 워크트리): `feat(console): 발송 로그 타입·소스·상태 한글 칩 전면화` (Co-Authored-By: Claude <noreply@anthropic.com>).

---

### Task 7 (web): 콘솔 데스크탑 사이드바 접기 토글

요청: 데스크탑에서 사이드바를 접었다 폈다 하는 토글(콘텐츠 넓게 쓰기). 상태는 localStorage 영속.

**Modify:** `src/components/console/console-shell.tsx`, `src/components/console/console-topbar.tsx`(토글 버튼), 필요 시 `src/components/console/console-sidebar.tsx`

- [ ] `ConsoleShell`에 `collapsed` 상태 추가(localStorage 키 `flori_console_sidebar_collapsed`, 초기값 false; SSR 안전하게 마운트 후 동기화). 데스크탑 `ConsoleSidebar` 표시를 `collapsed`에 연동 — 펼침=`w-60`, 접힘=숨김(`md:hidden` 효과). 기존 모바일 Sheet 햄버거는 그대로 유지.
- [ ] `ConsoleTopbar`에 데스크탑 전용 접기/펼치기 토글 버튼 추가(예: lucide `PanelLeft`/`PanelLeftClose`, `aria-label`/`aria-pressed`, md 이상에서만 노출). 클릭 시 `collapsed` 토글. 모바일 햄버거(`onMenu`)와 충돌 없게 분리.
- [ ] 접근성·테마 토큰 준수(브랜드 색·`hover:bg-muted`). `transition-all` 금지(web 컨벤션). `npm run lint && npm run build` 통과 + 토글 동작 육안 확인. 커밋(web 워크트리): `feat(console): 데스크탑 사이드바 접기 토글(localStorage 영속)` (Co-Authored-By: Claude <noreply@anthropic.com>).

---

## 배포·검증(코드 외, 머지 후)

검수 통과 후 배포 env에 `SOLAPI_*` 7종 주입(repo 밖). dev 우선 e2e: 테스트 계정 제출 → 접수 알림톡 수신 → 승인/거절 → 각 알림톡 수신 + `/console/notification-logs`에 `sent`/`failed` 기록 확인. 이상 없으면 prod 주입.

## Self-Review 메모

- 스펙 커버리지: A(Task 3)·B(Task 2 시그니처+env)·C(Task 4)·D(Task 1+Task 2 기록) + web 가독성(Task 6)·사이드바 토글(Task 7) 모두 매핑됨.
- 데이터 모델: source="alimtalk"(채널), type="business_verification"(도메인) — Task 1 마이그레이션으로 source CHECK에 'alimtalk' 추가. web 칩 라벨(Task 6)이 이 값과 정합.
- 타입 정합: `sendBusinessApproved/Submitted/Rejected` 시그니처(userId 추가)가 Task 2 정의 ↔ Task 3·4 호출부 일치. `NotificationSendRecorder.record` 시그니처 Task 1 정의 ↔ Task 2 호출 일치. `hasCredentials()` 정의(Task 2 Step 1) ↔ 사용(Task 2 Step 3) 일치.
- 플레이스홀더 없음(모든 코드/명령 구체값).
