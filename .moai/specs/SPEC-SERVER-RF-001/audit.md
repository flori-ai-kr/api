# SPEC-SERVER-RF-001 — 코드베이스 점검 결과(AUDIT)

점검일: 2026-05-23. 대상: `src/main/kotlin/com/hazel/**` + `db/migration/**` (구현 완료된 14개 SPEC).
방법: 멀티테넌시 격리 전수 grep + 4차원 수동 점검 + 독립 코드리뷰(code-reviewer 에이전트) 교차검증.

## 0. 종합 결론

코드베이스는 **전반적으로 양호**하다. 직전 보안/클린아키텍처 리뷰(커밋 `382846e`)에서 주요 이슈가 이미 정리되어, 이번 점검에서 **데이터 유출(멀티테넌시) 결함은 0건**이다. 남은 항목은 대부분 중·저 우선순위의 일관성/견고성 개선이며, 그중 **동작 보존이 확실한 것만 적용**하고 동작이 바뀌는 항목은 근거와 함께 보류한다(과엔지니어링 금지 원칙).

## 1. 보안 [최우선] — 멀티테넌시 `user_id` 격리 전수 재확인

**결과: 격리 누락 0건.** 모든 도메인 단건 접근은 `findByIdAndUserId(id, currentUserId())`, 컬렉션은 `findByUserId*` / userId가 포함된 Specification·네이티브 쿼리를 사용한다.

기본 JpaRepository 메서드(`findById`/`findAll`/`existsById`/`deleteById`) 사용처를 전수 확인한 결과 모두 안전:

| 사용처 | 판정 | 근거 |
|--------|------|------|
| `SettingsServices.findById(currentUserId())` (UserPreferences) | ✅ | PK가 user_id, 호출부가 항상 `currentUserId()` 전달 |
| `MeController.findById(userId)` / `AuthService.findById(stored.userId)` | ✅ | 본인 레코드(인증 흐름) |
| `InsightIngestService.findById/existsById/deleteById` (Instagram 계정) | ✅ | **의도적 전역 데이터**(공유 읽기, 내부 API 관리 — 테넌트 무관) |
| `ScrapService.existsById(targetId)` (트렌드/포스트) | ✅ | 전역 공유 콘텐츠 존재 확인(스크랩 본체는 격리됨) |
| `SubscriptionService.existsById(userId)` | ✅ | 웹훅 app_user_id 매핑용 사용자 존재 확인 |
| `SaleService.findAll(spec)` / `DepositService.findAll(spec)` | ✅ | Specification에 userId 포함 |

네이티브 SQL(JdbcTemplate) 8개 사용처: `Customer/Deposit/Dashboard/DepositCalculator`는 userId 바인딩, `DefaultDataSeeder`는 신규 사용자 id, **의도적 cross-tenant 2건**(`BroadcastService` 전체 브로드캐스트, `ReservationNotificationService` 토큰 비활성 — endpoint UNIQUE라 단일행)도 정상.

그 외: 시크릿 하드코딩 0(전부 `${ENV}`), 에러 응답 내부정보 비노출(`GlobalExceptionHandler`), SQL 인젝션 방지(전부 파라미터 바인딩), Bearer 비교 timing-safe(`BearerSecret`), JWT 기본시크릿 운영 부팅 가드.

## 2. 클린아키텍처

**판정: CLEAN.** controller→service→repository 경계가 12개 도메인 전반에서 일관 준수. 엔티티가 컨트롤러 밖으로 노출되는 곳 0(전부 DTO + `from()` 정적 팩토리). `common/` 배치 적절, 순환 의존 없음.

세부 관찰(저우선):
- `DepositCalculator`(sales 도메인)가 raw SQL로 `card_company_settings`(settings 도메인 테이블)를 읽음 → 교차 도메인 접근이지만 userId 바인딩되어 안전. **핵심 계산(수수료 SSOT) 경로라 동작 변경 위험이 커서 리포지토리 전환 대신 주석으로 의도 명시.**
- `DashboardService`가 `SaleService`/`ReservationService`를 주입(리포지토리 대신) → 이미 테넌트 격리된 서비스 재사용으로 **DRY 측면에서 의도적·안전**. 유지.

## 3. 확장성

**판정: GOOD.** 새 도메인 추가가 기존 코드 수정 없이 가능(패키지 추가만). `LabelSettingService<T>` 제네릭 베이스로 4개 라벨설정 중복 제거, `BearerSecret`를 내부 API/웹훅 검증이 공유. **YAGNI 위반(미사용/과추상) 없음.**

## 4. 유지보수성

| 항목 | 우선순위 | 조치 |
|------|----------|------|
| `monthRange()`가 잘못된 month 입력에 500 + Discord 알림 발생(NumberFormat/DateTimeParse 예외가 fallback 핸들러로) | **중** | **적용(R1)** — 400(VALIDATION)으로 변환 + 테스트 |
| 예약 상태 문자열이 `ReservationService` private companion에만 존재(`DepositStatuses`/`PaymentMethods`와 불일치) | 저 | **적용(R2)** — `common/domain/ReservationStatuses`로 추출 |
| `SettingsServices.kt`에 서비스 3개가 한 파일 | 저 | **적용(R3)** — 파일 분리 |
| 교차 도메인 raw SQL/리포지토리 의존 의도 불명확 | 저 | **적용(R4)** — 주석 명시 |
| `RecurringExpenseService` 크기/복잡도 | 중 | 보류 — 4분기 로직은 도메인 본질, 분해 시 가독성 저하 |

## 5. 적용 vs 보류 (우선순위 종합)

**적용(behavior-preserving 확실):**
- **R1** `monthRange` 잘못된 입력 → 400 (유효 입력 동작 불변, 오류 경로만 교정) + 테스트
- **R2** `ReservationStatuses` 공통 상수 추출 (순수 리팩터)
- **R3** `SettingsServices.kt` → 3개 파일 분리 (순수 구조)
- **R4** 교차 도메인 접근 주석 명시(`DepositCalculator`, `UserPreferencesRepository`)

**보류(동작 변경 → behavior-preserving 위배, 별도 SPEC 후보):**
- `deleteRecurringFromInstance`에 `RecurringSkip` 추가 — `ON CONFLICT DO NOTHING`이 이미 중복행 방지(현재 동작 정상). 스킵행 추가는 데이터 변경이라 보류.
- `BroadcastService` 전체 토큰 무제한 동기 발송 — `LIMIT`/async는 발송 범위/타이밍 변경. 현재 내부 API·소규모 전제, 스케일 시점에 별도 처리.
- `InsightService.posts()` region 인메모리 후필터(`REGION_BUFFER=3`) → JPQL 푸시다운 — 페이지네이션 결과 변동. best-effort 동작 유지.
- `DashboardService` 라벨 하드코딩 맵 vs 사용자 라벨 — 현재 값(코드)과 표시명(설정) 분리가 의도. 주석으로 충분.
