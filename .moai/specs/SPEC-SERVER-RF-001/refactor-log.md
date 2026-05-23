# SPEC-SERVER-RF-001 — 리팩터 로그

동작 보존(behavior-preserving) 원칙 하에 적용한 리팩터 기록. 각 항목: 무엇을 / 왜 / 효과.
전체 게이트: `./gradlew build test` **165테스트 통과(스킵 0, 실패 0)** — 직전 159 + 신규 6(DateRangesTest).

## R1 — `monthRange()` 잘못된 입력을 400(VALIDATION)으로 거부

- **무엇을**: `common/util/DateRanges.kt`의 `monthRange()` 파싱을 `try/catch(NumberFormatException, DateTimeException)`로 감싸 `AppException(ErrorCode.VALIDATION)`을 던지도록 변경. 특성화 테스트 `DateRangesTest`(6케이스) 추가.
- **왜**: 기존에는 `month=ABCD` 같은 잘못된 입력이 파싱 예외(RuntimeException)로 새어 `GlobalExceptionHandler`의 fallback(500 + Discord 알림)을 탔다. 클라이언트 입력 오류가 500/운영 알림으로 잡히는 것은 부정확하고 노이즈.
- **효과**: 유효 입력 동작은 100% 동일. 잘못된 입력만 500→400으로 교정(올바른 REST 시맨틱 + Discord 오탐 제거). `/sales`·`/expenses`·`/deposits`·`/dashboard/month` 등 month 파라미터 전 경로에 적용.

## R2 — 예약 상태 상수를 `common/domain/ReservationStatuses`로 추출

- **무엇을**: `ReservationService`의 private companion 상수(`STATUS_PENDING`/`STATUS_CANCELLED`/`STATUSES`)를 `common/domain/ReservationStatuses`(PENDING/CONFIRMED/COMPLETED/CANCELLED + ALL)로 이동하고 참조 교체.
- **왜**: `DepositStatuses`/`PaymentMethods`는 이미 `common/domain`에 있는데 예약 상태만 서비스 내부에 흩어져 일관성 부족. 향후 다른 도메인(대시보드 필터 등)이 예약 상태를 참조할 때 문자열 중복 위험.
- **효과**: 동작 동일(같은 문자열·검증 로직). 상태 문자열 SSOT 일원화, 재사용성↑.

## R3 — `SettingsServices.kt` 3개 파일로 분리

- **무엇을**: 한 파일에 있던 `CardCompanyService`/`UserPreferenceService`/`PushSubscriptionService`를 동명의 파일 3개로 분리(각자 필요한 import만).
- **왜**: 파일명이 주요 export와 불일치하고 3개 public 빈이 한 곳에 묶여 발견성이 낮음(도메인 성장 시 가중).
- **효과**: 동작·빈 구성 동일(클래스/패키지 불변). 탐색성·유지보수성↑.

## R4 — 교차 도메인 접근 의도 주석 명시

- **무엇을**: `DepositCalculator`의 `card_company_settings`(settings 도메인) 네이티브 조회에 "핫패스라 네이티브 유지·user_id 격리·컬럼 변경 시 동반 수정" 주석 추가. `UserPreferenceService`에 "PK=user_id, 항상 currentUserId 키 조회" 주석 추가.
- **왜**: 교차 도메인 raw SQL/조회는 안전하지만 의도가 코드만으로는 불명확 → 미래 유지보수자의 오해/회귀 방지.
- **효과**: 코드 동작 무변경, 문서성만 향상.

## 보류 항목(동작 변경 → 별도 SPEC 후보)

`audit.md` §5 참조. 요약:
- `deleteRecurringFromInstance`의 `RecurringSkip` 추가 — `ON CONFLICT`가 이미 중복행 방지(현 동작 정상), 스킵행 추가는 데이터 변경.
- `BroadcastService` 무제한 동기 발송에 `LIMIT`/async — 발송 범위/타이밍 변경.
- `InsightService.posts()` region 인메모리 후필터 → JPQL 푸시다운 — 페이지네이션 결과 변동.
- 위 3건은 behavior-preserving 위배라 본 SPEC 범위에서 제외.

## 멀티테넌시 전수 재확인 결과

`audit.md` §1 참조 — **격리 누락 0건**. 기본 JpaRepository 메서드(`findById`/`findAll`/`existsById`) 사용처 전수 확인: 전부 `currentUserId()` 키이거나 의도적 전역 데이터(Instagram 계정·트렌드/포스트 공유 콘텐츠). 네이티브 SQL 8건도 userId 바인딩 또는 의도적 cross-tenant 2건(브로드캐스트·토큰비활성, 후자는 endpoint UNIQUE 단일행). 기존 도메인별 격리 통합테스트가 회귀를 방지한다.
