# Hazel Server — ROADMAP

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
> ✅ **모든 SPEC 완료 (2026-05-23)**: Phase 1(13) + Phase 2(1) + 품질개선 RF-001 = 15개 전부 DONE. 다음 TODO 없음 — 자율 loop 정지 상태.

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
