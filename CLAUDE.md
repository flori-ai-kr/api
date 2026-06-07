# CLAUDE.md

이 파일은 Claude Code가 이 프로젝트에서 작업할 때 참조하는 가이드입니다.

---

## 프로젝트 개요

**Flori API** — 꽃집 어드민(Flori)의 모바일/웹 백엔드. 기존 Next.js 웹의 비즈니스 로직을 **앱/웹이 호출 가능한 REST API**로 재구현하고, 자체 AWS 인프라 위에서 동작시킨다.

자매 repo: `~/Desktop/flori-ai/mobile`(React Native / Expo), `~/Desktop/flori-ai/web`(Next.js).

### 기술 스택

| 구분 | 기술 |
|------|------|
| 언어/빌드 | Kotlin + Gradle (Kotlin DSL), Java 21 toolchain |
| 프레임워크 | Spring Boot 3.5 |
| 데이터 접근 | Spring Data JPA + Hibernate (jsonb/배열은 hypersistence-utils), 통계 집계는 네이티브 SQL |
| Database | AWS RDS PostgreSQL (로컬: Docker `flori-pg`) |
| 스키마 관리 | DDL 직접 관리(`docs/sql/*.sql`, Flyway 미사용) + `ddl-auto: validate`로 엔티티↔DB 정합 검증 |
| 인증 | Spring Security + 자체 JWT(access + refresh rotation) + **registerToken**(가입 대기, 5분). **소셜 전용**(카카오·구글·네이버 OAuth), 비밀번호 없음 |
| 검증 | Jakarta Bean Validation |
| 스토리지 | AWS S3 + CloudFront (presigned PUT URL 발급) |
| 푸시 | FCM (Firebase Admin SDK, 모바일) + Web Push/VAPID (브라우저 PWA). 구독의 p256dh/auth 유무로 전송 경로 분기(`PushDispatcher`) |
| 스케줄 | Spring `@Scheduled` (KST) |
| 에러 | `@ControllerAdvice` 표준 응답(`E-{DOMAIN}-{NNN}` 코드 체계) + Discord 웹훅 |
| API 문서 | Spring REST Docs → OpenAPI 3 (springdoc swagger-ui) — 테스트가 문서의 단일 출처. `docs(...)` 호출 시 `requestSchema`/`responseSchema`에 DTO 클래스명을 **항상 명시** (미지정 시 해시 이름 생성 → mobile orval 코드젠 타입 깨짐). 배열 응답은 `XxxListResponse` 별도 이름 부여 |
| 품질 | ktlint + detekt + JUnit(Zonky embedded PostgreSQL) + JaCoCo line 80% 게이트 |

---

## 빌드 및 실행

```bash
./gradlew build test     # ktlint + detekt + 전체 테스트 + JaCoCo 80% 게이트 (embedded PostgreSQL)
./gradlew ktlintFormat   # 자동 포맷
./gradlew openapi3        # RestDocs 테스트에서 OpenAPI 스펙 재생성 (→ static/docs/open-api-3.0.1.json)
./gradlew bootRun         # 로컬 실행 (기본 프로필: local)
open http://localhost:8080/swagger-ui.html   # API 계약 (테스트로 생성된 스펙)
```

| 프로필 | DB | 용도 |
|--------|-----|------|
| `local` | Docker `flori-pg` (bootRun) / Zonky embedded (test) | 로컬 개발·테스트 |
| `prod` | AWS RDS PostgreSQL | 운영 서버 (강한 `JWT_SECRET` 미설정 시 부팅 거부) |

> 테스트는 Zonky embedded PostgreSQL로 동작 → `./gradlew test`에 로컬 DB 불필요. `test` 프로필에서 `spring.sql.init`가 `docs/sql/*.sql`을 적용하고, 헬스체크는 `GET /health`.

---

## 프로젝트 구조

```
src/main/kotlin/kr/ai/flori/
├── auth/                  # 소셜 로그인/가입, JWT 발급·refresh rotation, registerToken
├── user/                  # 사용자 / 내 정보(/me)
├── sales/                 # 매출 기록 · 미수(unpaid) 처리
├── expenses/              # 지출 + 고정비 자동 생성(@Scheduled)
├── customers/             # 고객 (find-or-create, 실시간 통계)
├── reservations/          # 예약 (판매 전환, 픽업)
├── schedules/             # 일정 (리마인더 푸시)
├── photos/                # 갤러리 (presigned 업로드) · 태그
├── settings/              # 카드사 · 매출/지출 설정 · 하단바 · 푸시 구독
├── community/             # 커뮤니티 게시판(단일 공유) · 비밀글/댓글·대댓글·좋아요·soft delete
├── verification/          # 사업자 인증 (신청·상태조회·presigned 업로드·게이팅)
├── dashboard/             # 오늘/월 집계 · 네이티브 SQL 통계
├── admin/                 # 운영자 콘솔 API (/admin/**, @RequiresAdmin · cross-tenant) — 통계·인증심사·유저·AI헬스 프록시
├── ai/                    # AI 게이트웨이 (/ai/**) — web↔ai-server(FastAPI) 중개 + 모든 AI 호출 DB 로깅. 채팅/proactive/OCR예약/confirm. ai-server는 내부망 stateless
└── common/                # 횡단 관심사
    ├── config/            # CORS, OpenAPI, Async, Schedule, Web
    ├── domain/            # 공통 enum (PaymentMethods, ReservationStatuses)
    ├── entity/            # BaseEntity (Auditing)
    ├── error/             # GlobalExceptionHandler, AppException, ErrorCode, Discord 리포팅
    ├── health/            # 헬스체크
    ├── log/               # TraceIdFilter, LoggingInterceptor
    ├── notification/      # Discord 알림 채널 (DiscordNotifier, DiscordChannel, DiscordProperties)
    ├── push/              # PushService (FCM / 로깅 fallback)
    ├── request/           # ClientContext(ThreadLocal) + ClientContextFilter (요청 컨텍스트 캡처)
    ├── security/          # JWT, SecurityConfig, 내부 인증
    ├── storage/           # S3 presign
    ├── tenant/            # TenantContext (멀티테넌시)
    ├── util/              # DateRanges 등
    └── validation/        # 입력 길이 상한 SSOT (FieldLimits)
```

### 도메인별 레이어 패턴

```
{domain}/ → controller/ → service/ → repository/
                                   → entity/
                                   → dto/        # 요청/응답 DTO (엔티티 노출 금지)
                                   → error/      # 도메인 에러 코드
```

---

## 아키텍처 원칙 (HARD)

- **클린 아키텍처**: `controller → service → repository`. 도메인별 패키지(`kr.ai.flori.<domain>`). 횡단 관심사는 `kr.ai.flori.common/`. 도메인 에러 코드는 `<domain>/error/`.
- **DTO 경계**: 엔티티를 컨트롤러 밖으로 노출하지 않는다. 요청/응답 DTO 분리.
- **멀티테넌시 = 보안 1순위**: 모든 데이터 쿼리는 JWT에서 추출한 `userId`(`TenantContext`)로 격리한다. `user_id` 필터 누락은 곧 데이터 유출. RLS가 없으므로 애플리케이션이 유일한 방어선. 신원은 요청 본문이 아닌 토큰/TenantContext에서만 도출한다.
- **검증은 시스템 경계에서**: 컨트롤러 진입점 `@Valid`.
- **계산은 서버가 SSOT**: 지출총액(`unit_price * quantity`) 등은 서버가 계산해 응답.
- **AI 게이트웨이**: `ai/` 도메인이 web↔ai-server(FastAPI, 내부망)를 **중개**한다. web은 `/ai/*`만 호출하고 ai-server를 모른다. 게이트웨이가 유저 JWT 검증 후 ai-server를 `X-Internal-Key`(신뢰) + `X-User-Id` + JWT(도구 패스스루)로 호출하고, **대화 세션·메시지·OCR 쓰기제안·proactive 로그를 자기 DB(`ai_*` 테이블, FK 없음 간접참조)에 적재**한다. 쓰기(예약 생성)는 human-in-loop — `/ai/confirm`이 `ReservationService`로 직접 생성. (LLM 호출은 ai-server→litellm→Bedrock.)
- **시크릿은 환경변수**: 코드/깃에 시크릿 금지. `application.yml`은 `${ENV}` 참조만.

---

## 코딩 컨벤션

> **상세 내용과 코드 예시는 `docs/conventions/` 및 아래 레퍼런스 문서 참조**

| 컨벤션 | 핵심 규칙 | 상세 문서 |
|--------|-----------|-----------|
| 멀티테넌시 격리 | 모든 쿼리 `user_id` 격리, 신원은 토큰에서만 도출 | `docs/conventions/26-05-25-multitenancy-isolation.md` |
| 엔티티 Auditing·업데이트 | `BaseEntity` 자동 시각 관리, 상태 전이는 도메인 메서드(클래스 레벨 `@Setter` 금지) | `docs/conventions/26-05-25-entity-auditing-and-update-convention.md` |
| 레이어 패턴·레시피 | 도메인 추가 레시피, 멀티테넌시 적용 패턴 | `docs/PATTERNS.md` |
| Kotlin 관용구 | 이 repo에서 쓰는 Kotlin/Spring 관용구 | `docs/KOTLIN.md` |
| 에러 코드 | `E-{DOMAIN}-{NNN}` 코드 체계 | `docs/ERROR_CODES.md` |

---

## 보안 체크리스트 (HARD)

- JWT: 짧은 access TTL + refresh rotation(멱등 윈도 `JWT_REFRESH_DEDUP_TTL` 기본 30초 — 동시 race 로그아웃 방지, Caffeine 인메모리 캐시), registerToken 5분, 서명키 환경변수, 만료/위변조 검증
- 멀티테넌시: 모든 쿼리 `user_id` 격리
- 입력 검증: Bean Validation, SQL 인젝션 방지(JPA·네이티브 쿼리 파라미터 바인딩)
- S3: presigned URL 짧은 만료, 소유권 검증 후 발급
- CORS: 앱/웹 origin 화이트리스트
- 에러 응답: 내부 디테일(스택/쿼리) 노출 금지, 일반 메시지 + Discord에만 상세
- OWASP Top 10 준수

---

## 주요 클래스 위치

| 용도 | 위치 |
|------|------|
| 멀티테넌시 컨텍스트 | `common/tenant/TenantContext.kt` |
| 요청 컨텍스트 (클라이언트 정보) | `common/request/ClientContext.kt`, `ClientContextFilter.kt` |
| JWT 발급/검증 | `common/security/JwtTokenProvider.kt`, `JwtAuthenticationFilter.kt` |
| Security 설정 | `common/security/SecurityConfig.kt` |
| 내부 ingest 인증 | `common/security/InternalAuthVerifier.kt` |
| 글로벌 예외 처리 | `common/error/GlobalExceptionHandler.kt`, `AppException.kt`, `ErrorCode.kt` |
| Discord 에러 리포팅 | `common/error/DiscordErrorReporter.kt` |
| Auditing 베이스 엔티티 | `common/entity/BaseEntity.kt` |
| S3 presign | `common/storage/S3PresignService.kt` |
| 푸시 | `common/push/PushService.kt`, `FirebasePushService.kt` |
| 사업자 인증 게이팅 | `verification/gating/RequiresBusinessVerified.kt`, `BusinessVerifiedInterceptor.kt` |
| 운영자 콘솔 게이팅 | `admin/gating/RequiresAdmin.kt`, `AdminInterceptor.kt` (User.isAdmin 재검증, `/admin/**`) |
| Discord 알림 | `common/notification/discord/DiscordNotifier.kt`, `DiscordChannel.kt`, `DiscordProperties.kt` |
| CORS / OpenAPI 설정 | `common/config/CorsConfig.kt`, `OpenApiConfig.kt` |
| 헬스체크 | `common/health/HealthController.kt` |
| 입력 길이 상한 SSOT | `common/validation/FieldLimits.kt` |
| AI 일일 사용량 캡 (원자적 강제) | `ai/service/AiUsageGuard.kt` |

---

## 커밋 규칙

- `git add -A` 금지 → 변경 파일만 명시 추가
- conventional commits, 한국어 메시지 (`git log`로 기존 스타일 확인 후 따름)
- 빌드/테스트 통과 후에만 커밋 (`./gradlew build test`)
- Co-Authored-By: Claude <noreply@anthropic.com>
- force push 금지

---

## 참고 문서

```
docs/
├── ARCHITECTURE.md        # 시스템 아키텍처 & 기술 선정 이유 (Mermaid)
├── DATABASE.md            # DB 스키마 명세 (SSOT)
├── DESIGN.md              # 설계 SSOT, 배경 & 범위
├── ERROR_CODES.md         # 에러 코드 체계
├── KOTLIN.md              # Kotlin/Spring 관용구
├── PATTERNS.md            # 레이어 패턴, 멀티테넌시, 신규 도메인 레시피
├── conventions/           # 컨벤션 ADR (결정과 근거, 작업 전 필독)
├── guides/                # how-to 가이드
├── plans/                 # 구현 계획
└── troubleshooting/       # 문제 해결 기록
```

## 문서화 규칙

- 모든 문서는 한국어로 작성한다. 코드/식별자/함수명/타입은 영어.
- 코드와 문서를 함께 갱신한다.
- `docs/conventions/`·`guides/`·`plans/`·`troubleshooting/` 하위 문서 파일명은 `yy-mm-dd-{슬러그}.md`(2자리 연도). 단, 루트의 대문자 레퍼런스 문서(`ARCHITECTURE.md` 등)는 네이밍을 그대로 유지한다.
