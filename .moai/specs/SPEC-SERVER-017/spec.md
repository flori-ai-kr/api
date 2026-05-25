# SPEC-SERVER-017 — BaseEntity/Auditing + 엔티티 업데이트 컨벤션 (C1+C3)

## 목표
엔티티마다 중복되던 `createdAt`/`updatedAt` 보일러플레이트와 서비스의 수동 시각 갱신을 **공통 베이스 + Hibernate Auditing**으로 통합한다(C1). 더불어 다중 필드 상태 전이는 서비스가 흩뿌리지 않고 **엔티티 도메인 메서드**로 캡슐화하는 컨벤션을 도입한다(C3). 동작 보존.

## 배경
- 출처: `onetime/backend`의 `BaseEntity`(@CreatedDate/@LastModifiedDate Auditing) + `socc-assistant-api`의 엔티티 업데이트 컨벤션 ADR(단일 필드 setter, 다중 필드/규칙은 도메인 메서드 — Anemic 안티패턴 회피).
- 기존 hazel: 19개 엔티티가 각자 `createdAt/updatedAt` 컬럼을 선언하고, 24곳의 서비스가 `entity.updatedAt = Instant.now()`를 수동 호출 → 중복·누락 위험.

## 구현 (C1)
- `common/entity/BaseEntity.kt` 신설:
  - `BaseCreatedEntity`(@MappedSuperclass): `@CreationTimestamp createdAt`(생성 시각, INSERT 시 자동).
  - `BaseEntity : BaseCreatedEntity()`(@MappedSuperclass): `@UpdateTimestamp updatedAt`(INSERT/UPDATE 시 자동).
- 엔티티 전환:
  - **both(생성+수정) → `BaseEntity`**: Sale, User, CalendarEvent, Customer, Expense, RecurringExpense, InsightScrap, InstagramAccount, PhotoCard, Reservation, Subscription, CardCompanySetting, PushSubscription.
  - **created-only → `BaseCreatedEntity`**: RefreshToken, RecurringSkip, TrendArticle, PhotoTag, SubscriptionEvent, LabelSetting(@MappedSuperclass → 4개 라벨 설정 상속).
  - **범위 외**: `UserPreferences`(updated_at만 있고 created_at 없음 → 베이스 부적합, 수동 유지), `InstagramPost`(타임스탬프 없음).
- 서비스 24곳의 `entity.updatedAt = Instant.now()` 제거(@UpdateTimestamp가 대체) + 사용처가 사라진 `Instant` import 정리.

## 구현 (C3 — 컨벤션 도입 + 예시)
- 컨벤션: 단일 필드 변경은 setter, **다중 필드/비즈니스 규칙이 있는 상태 전이는 엔티티 도메인 메서드**로.
- 예시: `Sale.markDepositCompleted()`(status+depositedAt 동시 전이) / `Sale.revertDeposit()`. `DepositService`가 두 필드를 직접 세팅하던 것을 도메인 메서드 호출로 대체.
- 컨벤션 전문(Rationale 포함)은 SPEC-021의 ADR 문서(`docs/conventions/`)에 기록.

## 인수기준
- [x] `BaseEntity`/`BaseCreatedEntity` 신설 + 19개 엔티티 전환
- [x] 서비스 수동 `updatedAt` 갱신 제거 + import 정리
- [x] `Sale` 입금 상태 전이 도메인 메서드(C3 예시)
- [x] `ddl-auto: validate` 통과(스키마 매핑 불변 — 컬럼명 동일)
- [x] `./gradlew build test` 전체 그린 — 167 테스트(0 실패/0 스킵)
- [x] 동작 보존(behavior-preserving)

## 트레이드오프 / 비고
- `@UpdateTimestamp`는 dirty 변경이 있을 때 Hibernate가 자동 갱신한다. 기존 수동 `Instant.now()`와 기능 동치(둘 다 수정 시 갱신). 차이: 변경 필드가 전혀 없으면 UPDATE가 발생하지 않아 updatedAt도 안 바뀜 — 실제 코드는 항상 실 필드 변경과 함께라 영향 없음(167테스트로 검증).
- `created_at`은 `updatable=false`로 INSERT 후 불변.
