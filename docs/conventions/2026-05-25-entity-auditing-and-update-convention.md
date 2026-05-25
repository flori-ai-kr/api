# 엔티티 Auditing · 업데이트 컨벤션

> 2026-05-25 · 상태: 적용 중 · 관련 SPEC: SERVER-017

## 배경/맥락

엔티티마다 `createdAt`/`updatedAt`을 직접 선언하고, 서비스가 수정 때마다 `entity.updatedAt = Instant.now()`를 수동 호출했다(24곳). 중복이 많고 누락 위험이 있었다. 또한 다중 필드 상태 전이를 서비스가 흩뿌려 세팅해 불변식이 분산됐다(Anemic 경향).

## 결정 (Best Practice)

1. **생성/수정 시각은 베이스에서 자동 관리**한다.
   - `BaseCreatedEntity`(@MappedSuperclass): `@CreationTimestamp createdAt`(INSERT 시 자동, `updatable=false`).
   - `BaseEntity : BaseCreatedEntity()`: `@UpdateTimestamp updatedAt`(INSERT/UPDATE 시 자동).
   - 새 엔티티는 시각 컬럼을 직접 선언하지 말고 베이스를 상속한다. 서비스는 `updatedAt`을 수동으로 set하지 않는다.
   - 생성 시각만 있는 append-only/이력 엔티티는 `BaseCreatedEntity`를 상속.
2. **단일 필드 변경은 setter, 다중 필드/비즈니스 규칙이 있는 상태 전이는 엔티티 도메인 메서드**로 캡슐화한다.
   - 예: 입금 완료 = `depositStatus` + `depositedAt` 동시 전이 → `Sale.markDepositCompleted()` / `Sale.revertDeposit()`. 서비스는 이 메서드를 호출만 한다.
3. **베이스를 쓰지 않는 예외**: 타임스탬프 컬럼 구성이 베이스와 다른 엔티티(예: `UserPreferences`는 `updated_at`만, `InstagramPost`는 타임스탬프 없음)만 베이스에서 제외한다.

## 근거 (Rationale)

- `@UpdateTimestamp`는 dirty 변경이 있을 때 Hibernate가 자동 갱신 → 수동 호출 누락 버그를 구조적으로 제거. 기존 수동 방식과 기능 동치(167→168테스트로 동작 보존 검증).
- 상태 전이를 엔티티 메서드로 모으면 불변식이 한곳에 생겨 "status는 바꿨는데 시각은 안 바꿈" 류 버그를 막는다(Anemic Domain Model 안티패턴 완화). 단, 계산 로직(수수료·입금예정일)은 여전히 도메인 서비스(`DepositCalculator`)에 둔다 — 과한 리치모델 회피.
- 전면 DDD(Aggregate/VO)는 이 규모에 과하므로 도입하지 않는다 — "도메인 메서드로 상태 전이 캡슐화"라는 가벼운 원칙만 채택.

## 공식 문서/참고

- Hibernate ORM — `@CreationTimestamp` / `@UpdateTimestamp`: https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html#mapping-generated-CreationTimestamp
- Spring Data JPA — Auditing(대안): https://docs.spring.io/spring-data/jpa/reference/auditing.html
- Martin Fowler — Anemic Domain Model: https://martinfowler.com/bliki/AnemicDomainModel.html

## 적용 범위·예외

- 적용: `com.hazel.common.entity.BaseEntity`/`BaseCreatedEntity`를 상속하는 19개 엔티티.
- 예외: `UserPreferences`(updated_at만), `InstagramPost`(타임스탬프 없음) — 수동 관리 유지.
