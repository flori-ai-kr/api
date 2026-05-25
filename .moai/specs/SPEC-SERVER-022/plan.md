# RestDocs 검증 API 문서 + 커버리지 80% — 구현 플랜 (SPEC-SERVER-022)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** springdoc 어노테이션 기반 Swagger를, Spring REST Docs + ePages `restdocs-api-spec`으로 **테스트가 생성·보증하는 OpenAPI 3 문서**로 전환하고 JaCoCo line 80% 게이트를 건다.

**Architecture:** 기존 통합테스트(`@SpringBootTest`+`@AutoConfigureMockMvc`+Zonky PG)에 `@AutoConfigureRestDocs`를 얹어 각 엔드포인트를 1회 호출하며 `document(resource(...))`로 OpenAPI를 산출 → `static/docs/open-api-3.0.1.json` → `OpenApiConfig`가 정적 스펙 + JWT bearerAuth를 병합해 `/v3/api-docs`로 노출 → springdoc swagger-ui가 병합본 표시(Authorize 버튼). `packages-to-scan` 더미로 컨트롤러 스캔 억제. 컨트롤러 문서 어노테이션은 제거. *(플랜 초안의 `api-docs.enabled=false`+`swagger-ui.url` 방식은 구현 중 OpenApiConfig 병합 방식으로 변경됨)*

**Tech Stack:** Kotlin, Gradle Kotlin DSL, Spring Boot 3.5.14, `com.epages.restdocs-api-spec` 0.19.5, `spring-restdocs-mockmvc` 3.x, springdoc 2.8.17(뷰어), JaCoCo, Zonky embedded PostgreSQL.

**브랜치:** `feature/SPEC-SERVER-022-restdocs` (이미 생성, off dev). 완료 시 dev로 PR.

---

## File Structure

- `build.gradle.kts` — 플러그인(`restdocs-api-spec`, `jacoco`)·테스트 의존성·`openapi3{}`·스니펫/copy 태스크·jacoco 게이트
- `src/main/resources/application.yml` — springdoc 뷰어 설정(정적 스펙)
- `src/test/kotlin/kr/ai/flori/common/docs/RestDocsSupport.kt` — **신규** 공용 추상 베이스(RestDocs+MockMvc+Zonky+토큰 헬퍼)
- `src/test/kotlin/kr/ai/flori/<domain>/docs/<Xxx>DocsTest.kt` — **신규** 도메인별 RestDocs 문서화 통합테스트(엔드포인트당 1회 호출+document)
- `src/main/kotlin/kr/ai/flori/**/controller/*.kt` — `@Operation`/`@Tag` 제거(문서 출처가 테스트로 이전)
- `src/main/kotlin/kr/ai/flori/**/dto/*.kt` — `@Schema` 제거(JWT SecurityScheme는 `OpenApiConfig` 유지)
- `.github/workflows/ci.yml` — jacoco 리포트/검증 반영
- `README.md`·`docs/PATTERNS.md`·`ROADMAP.md`·`HANDOFF.md` — 갱신

---

## Task 1: 빌드 배선 (restdocs-api-spec + jacoco + openapi3)

**Files:**
- Modify: `build.gradle.kts`

- [ ] **Step 1: 플러그인 추가**

`build.gradle.kts`의 `plugins { }` 블록 끝(detekt 다음)에 추가:

```kotlin
    id("com.epages.restdocs-api-spec") version "0.19.5"
    jacoco
```

- [ ] **Step 2: 테스트 의존성 추가**

`dependencies { }`의 test 블록(`embedded-postgres` 다음)에 추가:

```kotlin
    testImplementation("com.epages:restdocs-api-spec-mockmvc:0.19.5")
    testImplementation("org.springframework.restdocs:spring-restdocs-mockmvc")
```

- [ ] **Step 3: 스니펫 디렉토리 + openapi3 + jacoco 태스크 추가**

파일 맨 끝(`tasks.withType<Test> { useJUnitPlatform() }` 다음)에 추가:

```kotlin
// === RestDocs → OpenAPI3 (테스트가 문서의 단일 출처) ===
import com.epages.restdocs.apispec.gradle.OpenApi3Task

val snippetsDir = layout.buildDirectory.dir("generated-snippets")

tasks.withType<Test> {
    useJUnitPlatform()
    outputs.dir(snippetsDir)
}

openapi3 {
    setServer(System.getenv("API_SERVER_URL") ?: "http://localhost:8080")
    title = "Flori Server API"
    description = "Spring REST Docs로 생성·검증된 Flori 백엔드 API 계약"
    version = "0.0.1"
    format = "json"
    outputFileNamePrefix = "open-api-3.0.1"
    outputDirectory = "src/main/resources/static/docs"
}

// openapi3 산출물은 테스트 스니펫에 의존
tasks.named<OpenApi3Task>("openapi3") { dependsOn(tasks.test) }
tasks.named("bootJar") { dependsOn("openapi3") }

// === JaCoCo line 80% 게이트 ===
jacoco { toolVersion = "0.8.12" }

val coverageExclusions = listOf(
    "**/FloriServerApplicationKt.*",
    "**/FloriServerApplication.*",
    "**/common/config/**",
    "**/dto/**",
)

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        html.required.set(true)
        xml.required.set(true)
    }
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) { exclude(coverageExclusions) }
        }),
    )
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) { exclude(coverageExclusions) }
        }),
    )
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.named("check") { dependsOn(tasks.jacocoTestCoverageVerification) }
```

- [ ] **Step 4: 의존성 해석·컴파일 확인 (커버리지 게이트는 아직 미충족일 수 있으니 test만)**

Run: `./gradlew compileTestKotlin --no-daemon`
Expected: BUILD SUCCESSFUL (새 의존성 해석됨)

- [ ] **Step 5: 커밋**

```bash
git add build.gradle.kts
git commit -m "chore: RestDocs(restdocs-api-spec 0.19.5)+JaCoCo 빌드 배선 (SPEC-SERVER-022)"
```

---

## Task 2: springdoc 뷰어 설정 (정적 스펙 서빙)

**Files:**
- Modify: `src/main/resources/application.yml`

- [ ] **Step 1: springdoc 블록을 정적 스펙 뷰어로 전환**

`application.yml`의 springdoc 관련 설정을 다음으로 설정(없으면 최상위에 추가):

```yaml
springdoc:
  api-docs:
    enabled: false            # 런타임 어노테이션 스캔 끔 — 문서 출처는 테스트 생성 스펙
  swagger-ui:
    enabled: true
    url: /docs/open-api-3.0.1.json   # 테스트가 생성한 정적 OpenAPI를 swagger-ui가 로드
    path: /swagger-ui.html
```

- [ ] **Step 2: 빌드 그린 확인**

Run: `./gradlew test --no-daemon`
Expected: 기존 170 테스트 PASS (설정 변경은 동작 무영향)

- [ ] **Step 3: 커밋**

```bash
git add src/main/resources/application.yml
git commit -m "chore: springdoc을 정적 OpenAPI 뷰어로 전환 (api-docs 비활성) (SPEC-SERVER-022)"
```

---

## Task 3: RestDocsSupport 공용 베이스

**Files:**
- Create: `src/test/kotlin/kr/ai/flori/common/docs/RestDocsSupport.kt`

- [ ] **Step 1: 베이스 클래스 작성**

```kotlin
package kr.ai.flori.common.docs

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper
import com.epages.restdocs.apispec.ResourceSnippetParameters
import com.epages.restdocs.apispec.Schema
import com.fasterxml.jackson.databind.ObjectMapper
import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultHandler
import org.springframework.test.web.servlet.post
import java.util.UUID

/**
 * RestDocs 문서화 통합테스트 공용 베이스.
 * 실제 보안 필터 체인 + 실제 PostgreSQL(Zonky)에서 엔드포인트를 1회 호출하며 OpenAPI 스펙을 생성한다.
 */
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
abstract class RestDocsSupport {
    @Autowired protected lateinit var mockMvc: MockMvc

    @Autowired protected lateinit var objectMapper: ObjectMapper

    protected fun json(value: Any): String = objectMapper.writeValueAsString(value)

    /** 가입 후 access 토큰 발급(보호 엔드포인트 문서화용). */
    protected fun signupAndToken(): String {
        val res =
            mockMvc.post("/auth/signup") {
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("email" to "docs-${UUID.randomUUID()}@flori.dev", "password" to "password123"))
            }.andReturn().response.contentAsString
        return objectMapper.readTree(res).get("accessToken").asText()
    }

    /** Kotlin MockMvc DSL의 andDo { handle(...) }에 넣을 RestDocs 핸들러 빌더. */
    protected fun docs(
        identifier: String,
        tag: String,
        summary: String,
        requestFields: List<FieldDescriptor> = emptyList(),
        responseFields: List<FieldDescriptor> = emptyList(),
    ): ResultHandler =
        MockMvcRestDocumentationWrapper.document(
            identifier,
            com.epages.restdocs.apispec.ResourceDocumentation.resource(
                ResourceSnippetParameters.builder()
                    .tag(tag)
                    .summary(summary)
                    .apply { if (requestFields.isNotEmpty()) requestFields(requestFields) }
                    .apply { if (responseFields.isNotEmpty()) responseFields(responseFields) }
                    .build(),
            ),
        )
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew compileTestKotlin --no-daemon`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/test/kotlin/kr/ai/flori/common/docs/RestDocsSupport.kt
git commit -m "test: RestDocs 공용 베이스(RestDocsSupport) (SPEC-SERVER-022)"
```

---

## Task 4: Auth PoC — 패턴 확립 + OpenAPI 생성 검증

**Files:**
- Create: `src/test/kotlin/kr/ai/flori/auth/docs/AuthDocsTest.kt`

대상 엔드포인트: `POST /auth/signup`(201), `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`(204). 필드는 `AuthDtos.kt`(SignupRequest/LoginRequest/RefreshRequest/LogoutRequest/TokenResponse) 기준.

- [ ] **Step 1: 실패하는(=아직 없는) 문서화 테스트 작성**

```kotlin
package kr.ai.flori.auth.docs

import com.fasterxml.jackson.databind.ObjectMapper
import kr.ai.flori.common.docs.RestDocsSupport
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.post
import java.util.UUID

class AuthDocsTest : RestDocsSupport() {
    private fun email() = "auth-doc-${UUID.randomUUID()}@flori.dev"

    private val tokenResponseFields = listOf(
        fieldWithPath("accessToken").type(JsonFieldType.STRING).description("API 호출용 access 토큰(짧은 TTL)"),
        fieldWithPath("refreshToken").type(JsonFieldType.STRING).description("access 재발급용 refresh 토큰(로테이션)"),
        fieldWithPath("expiresIn").type(JsonFieldType.NUMBER).description("access 만료까지 남은 초"),
        fieldWithPath("tokenType").type(JsonFieldType.STRING).description("토큰 타입(Bearer)"),
    )

    @Test
    fun `signup 문서화`() {
        mockMvc.post("/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = json(mapOf("email" to email(), "password" to "password123", "name" to "사장님"))
        }.andExpect { status { isCreated() } }
            .andDo {
                handle(
                    docs(
                        identifier = "auth-signup",
                        tag = "Auth",
                        summary = "회원가입",
                        requestFields = listOf(
                            fieldWithPath("email").type(JsonFieldType.STRING).description("로그인 이메일"),
                            fieldWithPath("password").type(JsonFieldType.STRING).description("비밀번호(8~72자)"),
                            fieldWithPath("name").type(JsonFieldType.STRING).optional().description("표시 이름(선택)"),
                        ),
                        responseFields = tokenResponseFields,
                    ),
                )
            }
    }

    @Test
    fun `login 문서화`() {
        val em = email()
        mockMvc.post("/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = json(mapOf("email" to em, "password" to "password123"))
        }.andExpect { status { isCreated() } }

        mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = json(mapOf("email" to em, "password" to "password123"))
        }.andExpect { status { isOk() } }
            .andDo {
                handle(
                    docs(
                        identifier = "auth-login",
                        tag = "Auth",
                        summary = "로그인",
                        requestFields = listOf(
                            fieldWithPath("email").type(JsonFieldType.STRING).description("로그인 이메일"),
                            fieldWithPath("password").type(JsonFieldType.STRING).description("비밀번호"),
                        ),
                        responseFields = tokenResponseFields,
                    ),
                )
            }
    }
}
```

(`refresh`·`logout`도 동일 패턴으로 같은 클래스에 추가 — refresh: requestFields `refreshToken`, responseFields `tokenResponseFields`; logout: requestFields `refreshToken`, 응답 204 무본문이라 responseFields 생략.)

- [ ] **Step 2: 테스트 실행 → 통과 + 스니펫 생성 확인**

Run: `./gradlew test --tests 'kr.ai.flori.auth.docs.AuthDocsTest' --no-daemon`
Expected: PASS. `build/generated-snippets/auth-signup/` 등 생성.

- [ ] **Step 3: OpenAPI 스펙 생성 확인**

Run: `./gradlew openapi3 --no-daemon && ls -1 src/main/resources/static/docs/`
Expected: `open-api-3.0.1.json` 생성. 파일에 `auth-signup`/`auth-login` 경로 포함.

- [ ] **Step 4: (수동 1회) swagger-ui 확인**

Run: `./gradlew bootRun` 후 브라우저에서 `http://localhost:8080/swagger-ui.html` → Auth signup/login이 테스트 생성 내용으로 표시되는지 확인. (CI 아님, 로컬 1회 검증)

- [ ] **Step 5: 커밋**

```bash
git add src/test/kotlin/kr/ai/flori/auth/docs/AuthDocsTest.kt src/main/resources/static/docs/open-api-3.0.1.json
git commit -m "test: auth RestDocs PoC + OpenAPI 생성 패턴 확립 (SPEC-SERVER-022)"
```

---

## Task 5~13: 도메인별 RestDocs 문서화 (Task 4 패턴 반복)

각 도메인 태스크는 **동일 절차**: (1) `<Xxx>DocsTest : RestDocsSupport()` 생성 → (2) 각 엔드포인트를 1회 호출하며 `.andDo { handle(docs(...)) }` 부여(필드는 해당 도메인 DTO 기준, 보호 엔드포인트는 `signupAndToken()`으로 Authorization 헤더) → (3) `./gradlew test --tests '...DocsTest'` 통과 → (4) `./gradlew openapi3`로 스펙 갱신 → (5) 커밋(`test: <domain> RestDocs 문서화 (SPEC-SERVER-022)`).

대상(컨트롤러 → 주 엔드포인트). 필드는 각 컨트롤러가 쓰는 요청/응답 DTO를 읽어 `fieldWithPath`로 1:1 기술:

- [ ] **Task 5 — me**: `MeController` (`GET /me`, 그 외 프로필 수정 엔드포인트 있으면 포함)
- [ ] **Task 6 — sales**: `SaleController`(목록/생성/수정/삭제 등), 응답의 서버계산 필드(fee/expectedDeposit/expectedDepositDate/depositStatus) 반드시 기술
- [ ] **Task 7 — expenses**: `ExpenseController`, `RecurringExpenseController`(고정비 규칙·this/all-future)
- [ ] **Task 8 — customers**: `CustomerController`(find-or-create, 통계 포함 응답)
- [ ] **Task 9 — reservations/calendar**: `ReservationController`(매출전환/픽업), `CalendarEventController`
- [ ] **Task 10 — deposits/photos**: `DepositController`, `PhotoCardController`(presigned 업로드), `PhotoTagController`
- [ ] **Task 11 — insights**: `InsightController`, `ScrapController`, `InternalInsightController`(내부 API 키 인증 — 헤더 문서화)
- [ ] **Task 12 — settings**: `CardCompanyController`, `SaleSettingsController`, `ExpenseSettingsController`, `PushSubscriptionController`
- [ ] **Task 13 — dashboard/subscriptions**: `DashboardController`(집계), `SubscriptionController`(`GET /subscription`), `RevenueCatWebhookController`(Bearer 시크릿 헤더), `HealthController`(`GET /health`)

각 태스크 완료 후 `./gradlew openapi3`로 `open-api-3.0.1.json`을 갱신·커밋한다.

---

## Task 14: 컨트롤러 문서 어노테이션 제거

문서 출처가 테스트로 이전되었으므로 런타임 어노테이션을 정리한다. (JWT 보안 표기는 `OpenApiConfig`에 유지)

**Files:**
- Modify: `src/main/kotlin/kr/ai/flori/**/controller/*.kt` (`@Operation`/`@Tag` 22파일)
- Modify: `src/main/kotlin/kr/ai/flori/**/dto/*.kt` (`@Schema` 3파일)

- [ ] **Step 1: 어노테이션·import 제거**

각 컨트롤러에서 `@Operation(...)`/`@Tag(...)`과 관련 import(`io.swagger.v3.oas.annotations.*`) 삭제. DTO에서 `@field:Schema`/`@Schema`와 import 삭제. (예: `AuthController.kt`에서 `@Tag`/`@Operation` 4개 + import 2줄 제거)

- [ ] **Step 2: ktlint 자동정렬 + 빌드**

Run: `./gradlew ktlintFormat test --no-daemon`
Expected: PASS (어노테이션 제거는 동작 무영향)

- [ ] **Step 3: 잔여 swagger 어노테이션 0 확인**

Run: `grep -rl 'io.swagger.v3.oas.annotations' src/main --include='*.kt'`
Expected: 출력 없음 (OpenApiConfig의 SecurityScheme는 `io.swagger.v3.oas.models` — 다른 패키지라 무관)

- [ ] **Step 4: 커밋**

```bash
git add src/main/kotlin
git commit -m "refactor: springdoc 문서 어노테이션 제거(문서 출처=테스트로 이전) (SPEC-SERVER-022)"
```

---

## Task 15: JaCoCo 베이스라인 측정 → 80% 미달분 보강

**Files:**
- Create/Modify: 미달 서비스의 단위·통합 테스트

- [ ] **Step 1: 커버리지 리포트 생성·확인**

Run: `./gradlew jacocoTestReport --no-daemon && open build/reports/jacoco/test/html/index.html`
Expected: 전체 line % 확인. 80% 미만이면 미달 패키지/클래스 식별.

- [ ] **Step 2: 미달분 테스트 추가**

낮은 커버리지 클래스(주로 분기 많은 서비스·통계 SQL·에러 경로)에 단위/통합 테스트 추가. 각 테스트는 RED(실패)→GREEN(통과) 순으로 작성하고, 의미 있는 동작을 검증(커버리지 숫자만 채우는 빈 테스트 금지).

- [ ] **Step 3: 게이트 통과 확인**

Run: `./gradlew jacocoTestCoverageVerification --no-daemon`
Expected: BUILD SUCCESSFUL (line ≥ 80%)

- [ ] **Step 4: 커밋**

```bash
git add src/test build.gradle.kts
git commit -m "test: 커버리지 80% 미달분 보강 + 게이트 통과 (SPEC-SERVER-022)"
```

---

## Task 16: CI에 JaCoCo 반영

**Files:**
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: test 잡의 실행 태스크에 커버리지 검증 추가**

`ci.yml`의 test 잡 "Run tests" 스텝을 다음으로 변경:

```yaml
      - name: Run tests + coverage gate
        run: ./gradlew test jacocoTestCoverageVerification --no-daemon
```

- [ ] **Step 2: 커밋**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: JaCoCo 커버리지 게이트를 CI에 반영 (SPEC-SERVER-022)"
```

---

## Task 17: 문서 갱신 + 검증 게이트 + PR

**Files:**
- Modify: `README.md`(Tech stack에 RestDocs 반영, API docs 설명 갱신), `docs/PATTERNS.md`(RestDocs 작성 패턴 + 컨벤션), `ROADMAP.md`(022 → DONE), `HANDOFF.md`, `.moai/specs/SPEC-SERVER-022/spec.md`(인수기준 체크)

- [ ] **Step 1: 문서 갱신**

README Tech stack 표의 "API docs"를 `springdoc(뷰어) ← RestDocs(restdocs-api-spec)로 테스트 생성한 OpenAPI`로, "Lint & test"에 커버리지 게이트 명시. PATTERNS에 RestDocsSupport/`docs()` 사용법 추가. ROADMAP의 SPEC-SERVER-022를 DONE으로, spec.md 인수기준 체크.

- [ ] **Step 2: 최종 검증 게이트**

Run: `./gradlew clean build test jacocoTestCoverageVerification --no-daemon`
Expected: BUILD SUCCESSFUL (전체 빌드+테스트+커버리지 게이트 통과)

- [ ] **Step 3: 문서 커밋**

```bash
git add README.md docs/PATTERNS.md ROADMAP.md HANDOFF.md .moai/specs/SPEC-SERVER-022/spec.md
git commit -m "docs: SPEC-SERVER-022 완료 — RestDocs/커버리지 문서 갱신 + ROADMAP DONE (SPEC-SERVER-022)"
```

- [ ] **Step 4: dev로 PR**

```bash
git push -u origin feature/SPEC-SERVER-022-restdocs
gh pr create --base dev --head feature/SPEC-SERVER-022-restdocs \
  --title "feat: RestDocs 검증 API 문서 + 커버리지 80% (SPEC-SERVER-022)" \
  --body "SPEC-SERVER-022 구현. RestDocs(restdocs-api-spec)로 테스트가 OpenAPI 생성→swagger-ui 서빙, springdoc 어노테이션 제거, JaCoCo line 80% 게이트. .moai/specs/SPEC-SERVER-022/spec.md 참조."
```

(또는 사용자 규칙대로 `/feature-finalize`로 마무리.)

---

## Self-Review 체크 (작성자 확인 완료)

- **Spec 커버리지**: spec 인수기준 7개 → Task 1~2(빌드/뷰어), Task 3~13(RestDocs+22컨트롤러), Task 14(어노테이션 제거), Task 15~16(커버리지 80%+CI), Task 17(문서/PR)로 전부 매핑됨.
- **플레이스홀더**: 인프라·PoC는 완전 코드. 도메인 반복(Task 5~13)은 잠긴 패턴(Task 4) + 컨트롤러·엔드포인트 명시 + "DTO 읽어 1:1 기술" 지시 — 실행 주체가 실제 DTO에서 필드를 읽어 적용(빈 지시 아님).
- **타입 일관성**: `docs(identifier, tag, summary, requestFields, responseFields)` 시그니처는 Task 3 정의 → Task 4·5~13에서 동일 사용. `RestDocsSupport`/`signupAndToken()`/`json()` 일관.
- **함정 반영**: Kotlin DSL `andDo { handle(...) }`, openapi3 산출물 경로(static/docs), Zonky 의존, 커버리지 제외목록(`dto/**`·`common/config/**`·Application) 명시.
