# Flori Server — ROADMAP

자율 실행의 단일 진입점. 세션은 이 파일을 먼저 읽고, `status: TODO`이며 `deps`가 모두 `DONE`인 첫 SPEC을 골라 진행한다.
완료 시 해당 SPEC의 status를 `DONE`으로 바꾸고 `HANDOFF.md`를 갱신한다.

상태값: `TODO` | `DOING` | `DONE` | `BLOCKED`

## Phase 1 — 백엔드 (M1: 기반 / M2: 도메인 API)

| SPEC | status | deps | 범위 |
|------|--------|------|------|
| SPEC-SERVER-001 | DONE | — | **프로젝트 스켈레톤**: Gradle KTS + Spring Boot(Kotlin, Java21), 패키지 구조(common/), `application.yml`(env 참조), 헬스체크 엔드포인트, Dockerfile, springdoc-openapi, `.gitignore`, ktlint/detekt |
| SPEC-SERVER-002 | DONE | 001 | **DB + Flyway baseline**: RDS 연결 설정, Flyway, 원본 Supabase 스키마(20테이블) 이식 마이그레이션 작성 — RLS·`auth.users` FK 제거, 자체 `users` 테이블 추가, `user_id`가 `users` 참조. jsonb/배열/uuid/timestamptz 유지 |
| SPEC-SERVER-003 | DONE | 002 | **인증**: Spring Security + JWT(access+refresh rotation), BCrypt, `POST /auth/signup`(가입 시 사용자별 기본 카테고리/결제방식/카드사 시드), `/auth/login`, `/auth/refresh`, `/auth/logout`, JWT 필터 → `TenantContext` |
| SPEC-SERVER-004 | DONE | 003 | **공통 인프라**: `@ControllerAdvice` 표준 에러 응답 + Discord 웹훅 로깅, `TenantContext`(요청 스코프, userId), S3 presigned PUT 발급 서비스, FCM 발송 서비스, CORS, 보안 헤더 |
| SPEC-SERVER-005 | DONE | 004 | **매출 API**: CRUD + 무한스크롤(loadMore) + 자동완성 + 미수(unpaid) 완료/되돌리기 + 카드수수료/입금예정일 서버 계산 + 다중선택 필터(category/payment/channel `.in()`) |
| SPEC-SERVER-006 | DONE | 004 | **지출 + 고정비 API**: 지출 CRUD + 자동완성, 고정비(recurring) CRUD(this/future/all 분기) + 빠른추가, `@Scheduled` KST 00:30 고정비 자동 생성(recurring_skips 고려) |
| SPEC-SERVER-007 | DONE | 004 | **고객 API**: CRUD + 등급/성별 + findOrCreate(전화번호+user_id 복합) + 고객별 매출 조회 |
| SPEC-SERVER-008 | DONE | 005,007 | **예약 + 캘린더 API**: 예약 CRUD + 매출 전환 + 픽업완료 + 자동완성, 캘린더 이벤트 CRUD, `@Scheduled` 일일 요약(08:00 KST)·개별 리마인더(reminder_at) 푸시 |
| SPEC-SERVER-009 | DONE | 005 | **입금대조 API**: 카드 입금 목록 + 다건 확인 + 되돌리기 |
| SPEC-SERVER-010 | DONE | 004 | **사진첩 + 태그 API**: 사진카드 CRUD(매출 연동) + presigned 업로드 타깃 발급(소유권/메타 검증) + 태그 CRUD + 정렬 |
| SPEC-SERVER-011 | DONE | 004 | **인사이트 API**: 트렌드/인스타 계정·포스트 조회(공유 읽기), 스크랩 CRUD+메모(polymorphic), 읽음 처리, 내부 API(Bearer, 수집/브로드캐스트) |
| SPEC-SERVER-012 | DONE | 004 | **설정 API**: 카드사 수수료/입금일, 매출설정(카테고리/결제방식), 지출설정, 사용자설정(BottomNav JSONB), 푸시 구독 등록/해지 |
| SPEC-SERVER-013 | DONE | 005,006,007 | **대시보드 + 통계**: 오늘/월 집계, 다가오는 예약, 발동 리마인더, 카테고리/결제수단/채널/고객 통계(네이티브 SQL 집계) |

> 🟢 **Phase 2 활성화 (2026-05-23)**: 사용자 승인. SPEC-SERVER-014(결제/구독) 완료. **범위는 코드 구현만** — 실제 RevenueCat 계정/스토어 상품/심사/배포/키 발급 없음, 시크릿은 env placeholder, 테스트는 샘플/모의. 상세 명세: `.moai/specs/SPEC-SERVER-014/spec.md`.
>
> ✅ **기능 SPEC 완료 (2026-05-23)**: Phase 1(13) + Phase 2(1) + 품질개선 RF-001 = 15개 DONE.
> 🔧 **유지보수/컨벤션 라운드 진행 중 (2026-05-25)**: SPEC-015~021 — 의존성 최신화 + 외부 레포 검증패턴 이식. 아래 "유지보수 & 컨벤션 정착" 섹션 참조.

## Phase 2 — 결제 (앱 출시 후)

| SPEC | status | deps | 범위 |
|------|--------|------|------|
| SPEC-SERVER-014 | DONE | ✅ 앱 Phase1 완료 | **구독/결제**: `subscriptions`/`subscription_events` 테이블, RevenueCat 웹훅 수신(Bearer 시크릿) → 상태 매핑·갱신, `GET /subscription`, `@RequiresSubscription` 게이팅(인터셉터), `user_id` 격리. 159테스트 통과 |

## 진행 규칙
- 한 세션은 SPEC을 **하나씩** 끝낸다(빌드·테스트·커밋까지). 그 후 다음 TODO로.
- 의존성 미충족 SPEC은 건너뛰지 말고, 충족된 가장 앞 SPEC을 택한다.
- 모든 SPEC은 `.moai/specs/<SPEC-ID>/spec.md`에 인수기준을 먼저 적고 구현한다.

## 품질 개선 (리팩터링 & 문서화)

> 사용자 지시(2026-05-23): 코드 완성 후 구조/품질 점검·개선. 동작 보존, 과엔지니어링 금지.

| SPEC | status | deps | 범위 |
|------|--------|------|------|
| SPEC-SERVER-RF-001 | DONE | — | **리팩터링 & 품질**: 4차원 audit(멀티테넌시 격리 누락 0건 재확인) → 동작보존 리팩터 4건(monthRange 400 검증·ReservationStatuses 추출·SettingsServices 분리·교차도메인 주석) → 문서화(audit.md·refactor-log.md). 165테스트 통과. 명세 `.moai/specs/SPEC-SERVER-RF-001/spec.md` |

## 유지보수 & 컨벤션 정착 (2026-05-25)

> 사용자 지시(2026-05-25): 의존성 최신화 + 기존 Java/Spring 레포(onetime/backend·batch, socc-assistant-api)의 검증된 패턴을 flori(Kotlin)에 선별 이식. 동작 보존, 과엔지니어링 금지.

| SPEC | status | deps | 범위 |
|------|--------|------|------|
| SPEC-SERVER-015 | DONE | — | **Spring Boot 3.5 업그레이드**: EOL된 3.4.1 → 3.5.14, springdoc 2.7.0 → 2.8.17. 동작 보존, 165테스트 통과. 명세 `.moai/specs/SPEC-SERVER-015/spec.md` |
| SPEC-SERVER-016 | DONE | 015 | **(A1) 멀티테넌시 격리 자동검출 테스트**: 리플렉션으로 모든 `kr.ai.flori` 리포지토리 선언 메서드가 `user_id` 격리(메서드명 UserId 또는 @Query user_id)되는지 전수 검증, 의도적 전역은 화이트리스트(자기검증 포함). 첫 실행에서 insights 공유콘텐츠 11종 검출. 167테스트 통과. 명세 `.moai/specs/SPEC-SERVER-016/spec.md` |
| SPEC-SERVER-017 | DONE | 015 | **(C1+C3) BaseEntity/Auditing + 엔티티 업데이트 컨벤션**: `common/entity` `BaseEntity`(@CreationTimestamp/@UpdateTimestamp)·`BaseCreatedEntity` 신설 → 19개 엔티티 전환, 서비스 수동 updatedAt 24곳 제거. 입금 상태 전이 도메인 메서드(C3 예시). ddl validate·167테스트 통과. 명세 `.moai/specs/SPEC-SERVER-017/spec.md` |
| SPEC-SERVER-018 | DONE | 015 | **(E1) 리치 OpenAPI 어노테이션**: JWT bearer 보안 스킴 전역 등록(Authorize 버튼) + ErrorResponse·AuthDtos·SaleDtos 핵심 필드 `@Schema`(설명/예시/허용값). 패턴 정착. 167테스트 통과. 명세 `.moai/specs/SPEC-SERVER-018/spec.md` |
| SPEC-SERVER-019 | DONE | 015 | **(D1+D2) 스케줄러 멱등성 + 실패격리**: `V5 notification_log` + 원자적 claim(ON CONFLICT)으로 일일요약 중복발송 차단(at-most-once). 리마인더·요약·고정비생성 건별 try-catch(DataAccessException)+@Transactional 제거로 PG abort 격리. 168테스트 통과. 명세 `.moai/specs/SPEC-SERVER-019/spec.md` |
| SPEC-SERVER-020 | DONE | 015 | **(B4) 에러리포팅 PII 마스킹+truncate**: 공통 `maskSensitive`(경로/이메일/전화/토큰/키) 추출 → 메시지·액션 필드까지 `sanitizeMessage`+truncate 적용, 전화번호 마스킹 추가. 170테스트 통과. 명세 `.moai/specs/SPEC-SERVER-020/spec.md` |
| SPEC-SERVER-021 | DONE | 015 | **(DOC1) 컨벤션 ADR 문서 체계**: `docs/conventions/`(README 인덱스 + 작성규칙 `yy-mm-dd-*.md`) 신설, 핵심 ADR 2건(멀티테넌시 격리·엔티티 Auditing/업데이트) 근거+공식링크와 함께 작성. PATTERNS.md에서 링크. 명세 `.moai/specs/SPEC-SERVER-021/spec.md` |
| SPEC-SERVER-022 | DONE | 018 | **(E2) RestDocs 검증 API 문서 + 커버리지 80%**: onetime 패턴 이식 — ePages `restdocs-api-spec` 0.19.2 + `spring-restdocs-mockmvc`로 기존 통합테스트에 RestDocs 연결 → OpenAPI3(107 path/120 op/22 tag) 생성 → springdoc 뷰어 서빙(`api-docs.enabled=false`, `swagger-ui.url=정적스펙`), 컨트롤러/DTO 문서 어노테이션 제거. JaCoCo **line 89.4%**(게이트 80%) check/CI 연결. 전 도메인 `*DocsTest`. 명세 `.moai/specs/SPEC-SERVER-022/spec.md` |
