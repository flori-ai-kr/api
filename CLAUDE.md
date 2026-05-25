# Flori Server — 꽃집 관리 백엔드 API

Flori(꽃집 어드민)의 모바일 앱 백엔드. Spring Boot(Kotlin) REST API.
이 repo는 **Flori 모바일 전환 프로그램의 백엔드 파트**다. 자매 repo: `~/Desktop/flori-ai/mobile`(Flutter), 원본 웹 `~/Desktop/flori-ai/web`(Next.js, 참고용).

## 이 repo의 역할

기존 Next.js 웹은 모든 로직이 Server Actions에 묶여 네이티브 앱이 호출할 수 없다.
이 백엔드는 그 비즈니스 로직을 **앱이 호출 가능한 REST API**로 재구현하고, 자체 AWS 인프라 위에서 동작시킨다.

## 기술 스택

| 영역 | 선택 |
|------|------|
| 언어/빌드 | **Kotlin + Gradle (Kotlin DSL)** |
| 프레임워크 | **Spring Boot 3.x** (Java 21 toolchain) |
| 데이터 접근 | **Spring Data JPA + Hibernate** (jsonb/배열은 hypersistence-utils), 통계 집계는 네이티브 SQL |
| DB | **AWS RDS PostgreSQL** |
| 스키마 관리 | **Flyway** (마이그레이션 버전 관리) |
| 인증 | **Spring Security + 자체 JWT** (access + refresh rotation), BCrypt |
| 검증 | **Jakarta Bean Validation** + 커스텀 validator |
| 스토리지 | **AWS S3 + CloudFront** (presigned PUT URL 발급) |
| 푸시 | **FCM** (Firebase Admin SDK) |
| 스케줄 | **Spring `@Scheduled`** (기존 Vercel Cron 대체) |
| 에러 로깅 | **@ControllerAdvice 표준 응답 + Discord 웹훅** |
| API 문서 | **Spring REST Docs + ePages `restdocs-api-spec` 0.19.2** — 테스트가 OpenAPI 3 스펙을 생성(SSOT). `OpenApiConfig`가 정적 스펙과 JWT bearerAuth를 병합해 `/v3/api-docs`로 노출, springdoc swagger-ui가 표시(Authorize 버튼). `packages-to-scan` 더미로 컨트롤러 스캔 억제. |
| 커버리지 | **JaCoCo line 80% 게이트** — `jacocoTestCoverageVerification`이 `check`/CI에 연결(현재 89%). 제외: Application·config·dto |

## 아키텍처 원칙 (HARD)

- **클린 아키텍처 / 레이어 분리**: `controller → service → repository`. 도메인별 패키지(`kr.ai.flori.<domain>`). 횡단 관심사는 `kr.ai.flori.common/`(security, error, tenant, s3, push, config).
- **DTO 경계**: 엔티티를 컨트롤러 밖으로 노출하지 않는다. 요청/응답 DTO 분리.
- **멀티테넌시 = 보안 1순위** [HARD]: 모든 데이터 쿼리는 JWT에서 추출한 `userId`로 격리한다. `TenantContext`(요청 스코프) + 서비스/리포지토리 레벨 강제. **`user_id` 필터 누락은 곧 데이터 유출**이므로 절대 빠뜨리지 않는다. RLS가 없으므로 애플리케이션이 유일한 방어선.
- **검증은 시스템 경계에서**: 컨트롤러 진입점에서 `@Valid`. ID는 UUID 형식 검증.
- **계산은 서버가 SSOT**: 카드수수료(`amount * (1 - fee_rate/100)`), 입금예정일(영업일 N일), 지출총액(`unit_price * quantity`) 등은 서버가 계산해 응답. 앱은 표시만.
- **시크릿은 환경변수**: 코드/깃에 시크릿 금지. `application.yml`은 `${ENV}` 참조만.
- **확장성**: 새 도메인 추가가 기존 코드 수정 없이 패키지 추가로 끝나도록.

## 보안 체크리스트 (HARD)

- JWT: 짧은 access TTL + refresh rotation, 서명키는 환경변수, 만료/위변조 검증
- 비밀번호: BCrypt, 평문 로깅 금지
- 멀티테넌시: 위 참조 — 모든 쿼리 `user_id` 격리
- 입력 검증: Bean Validation, SQL 인젝션 방지(JPA 파라미터 바인딩, 네이티브 쿼리도 바인딩)
- S3: presigned URL 짧은 만료, 소유권 검증 후 발급
- CORS: 앱/웹 origin 화이트리스트
- 에러 응답: 내부 디테일(스택/쿼리) 노출 금지, 일반 메시지 + Discord에만 상세
- 보안 헤더, rate limiting 고려
- OWASP Top 10 기준 준수

## 자율 실행 프로토콜 (원격 무인 loop)

이 repo는 원격에서 `/loop`로 무인 진행한다. **세션은 항상 다음 순서를 따른다:**

```
1. ROADMAP.md 읽기 → status: TODO 이면서 의존성(deps) 충족된 첫 SPEC 선택
2. .moai/specs/<SPEC-ID>/spec.md 없으면 → 상세 명세 작성(목표·인수기준·범위)
3. 구현 (TDD 권장: 실패 테스트 → 구현 → 리팩터)
4. 검증 게이트: ./gradlew build test   ← 반드시 통과
5. 통과 → 변경 파일만 커밋(conventional, 한국어) → ROADMAP 해당 SPEC을 DONE으로 → HANDOFF.md 갱신
6. 실패 시 재시도, 막히면 ROADMAP에 BLOCKED + HANDOFF에 블로커 상세 기록 후 정지
7. 다음 TODO SPEC으로 반복
```

### 병렬 실행 (앱과 동시 진행) [HARD]

`~/Desktop/flori-ai/mobile`이 이 repo와 동시에 돌아간다. **백엔드는 앱을 기다리지 않고** ROADMAP 순서대로 독립 진행한다.
단, **앱 세션이 이 repo의 `ROADMAP.md` 상태를 읽어 연동 시점을 판단**하므로, SPEC 완료 시 status를 정확히 `DONE`으로 갱신하는 것이 앱 연동의 신호다. 상태 갱신을 빠뜨리지 않는다.

### 커밋 규칙
- `git add -A` 금지 → 변경 파일만 명시 추가
- conventional commits, 한국어 메시지 (예: `feat: JWT 인증 + refresh 로테이션 (SPEC-SERVER-003)`)
- 각 SPEC = 최소 1커밋, **빌드/테스트 통과 후에만 커밋** (언제 멈춰도 깨끗한 상태 유지)
- Co-Authored-By: Claude <noreply@anthropic.com>
- force push 금지

### loop 시작 프롬프트 (이 repo를 cwd로 둔 새 세션에서 붙여넣기)
```
/loop CLAUDE.md의 자율 실행 프로토콜을 따라 ROADMAP.md의 다음 TODO SPEC을 하나 구현하고, ./gradlew build test 통과 후 변경 파일만 커밋하고 ROADMAP/HANDOFF를 갱신해. 막히면 BLOCKED 기록 후 멈춰.
```

## 참고 문서
- `docs/DESIGN.md` — 전체 아키텍처 설계 (SSOT)
- `ROADMAP.md` — SPEC 목록·순서·상태
- `HANDOFF.md` — 직전 세션 상태·다음 할 일
- 원본 웹 로직 참고: `~/Desktop/flori-ai/web/src/lib/actions/`, `~/Desktop/flori-ai/web/docs/ARCHITECTURE.md`
## 문서화 규칙 [HARD]

- **모든 문서는 한국어로 작성한다.** SPEC 명세(`.moai/specs/*/spec.md`), README, DESIGN, API 설명, ROADMAP/HANDOFF, 커밋 메시지까지 전부 한국어. 영어 금지 — 단 코드/식별자/함수명/타입은 영어.
- **각 SPEC은 문서를 갱신하며 진행한다**: (1) 착수 시 `spec.md`에 목표·인수기준·범위 기록, (2) 구현 중 주요 결정/트레이드오프를 `spec.md` 또는 `docs/`에 남김, (3) 완료 시 README/DESIGN/관련 문서 최신화 + ROADMAP/HANDOFF 갱신.
- 문서 없는 코드 커밋 금지 — 코드와 문서를 함께 커밋한다.
