# SPEC-SERVER-016 — 멀티테넌시 격리 자동검출 테스트 (A1)

## 목표
모든 도메인 리포지토리의 쿼리 메서드가 `user_id`로 테넌트 격리되는지 **자동 검증하는 회귀 가드 테스트**를 추가한다. 새 메서드가 격리를 빠뜨리면 테스트가 실패하도록 만들어, hazel의 1순위 HARD 원칙("`user_id` 필터 누락 = 데이터 유출")을 코드 레벨 가드레일로 정착시킨다.

## 배경
- hazel은 RLS가 없어 **애플리케이션이 유일한 방어선**(CLAUDE.md 보안 1순위).
- RF-001 audit에서 격리 누락 0건을 **수동** 확인했으나, 미래 변경으로 다시 깨질 수 있음 → 영구 자동 가드 필요.
- 출처 패턴: `onetime/backend`의 `SecurityAnnotationTest`(모든 컨트롤러의 권한 어노테이션 누락을 클래스패스 스캔으로 검출)를 hazel의 격리 모델에 맞게 이식.

## 범위
- `src/test/kotlin/com/hazel/common/tenant/TenantIsolationGuardTest.kt` 신규.
- 리플렉션으로 `com.hazel.*` 리포지토리 인터페이스의 **직접 선언 메서드**(상속된 JpaRepository CRUD 제외)를 전수 검사.
- 격리 판정: 메서드명에 `UserId` 포함 **또는** `@Query` 값이 `user_id`/`userId` 참조.
- 격리되지 않은 메서드는 **`intentionalGlobal` 화이트리스트(사유 명시)** 에만 허용.
- 새 의존성(ArchUnit 등) 추가 없음 — Spring Data `Repositories` API + 표준 리플렉션.

## 의도적 전역 메서드(화이트리스트) — RF-001 audit과 일치
- 인증(테넌트 데이터 아님): `UserRepository#findByEmail`, `#existsByEmail`, `RefreshTokenRepository#findByTokenHash`
- 스케줄러(전체 테넌트 시스템 작업): `RecurringExpenseRepository#findByIsActiveTrue`, `ReservationRepository#findDueReminders`, `#findByDateAndStatusNot`
- 자식 엔티티(이미 테넌트 검증된 부모 id로 접근): `RecurringSkipRepository#existsByRecurringIdAndSkipDate`, `#findByRecurringIdInAndSkipDate`
- 인사이트 공유 콘텐츠(엔티티에 `user_id` 컬럼 없음 — 전체 사용자 공유, SPEC-011): `TrendArticleRepository`·`InstagramAccountRepository`·`InstagramPostRepository`의 읽기/수집 메서드 11종. (`InsightScrapRepository`는 사용자별 → `UserId` 격리되어 화이트리스트 불필요)

> 가드 효과 실증: 작성 직후 첫 실행에서 `InsightRepositories.kt`(복수 파일명이라 수동 검색에서 누락됐던) 공유 콘텐츠 11개 메서드를 자동 검출 → 사각지대를 가드가 잡아냄을 확인.

## 인수기준
- [ ] 모든 `com.hazel` 리포지토리 선언 메서드가 격리되거나 화이트리스트에 있음 → 그린
- [ ] 화이트리스트 **자기검증**: 각 항목이 실재하는 메서드이며 실제로 비격리(격리되면 항목 제거하도록 유도)
- [ ] 검사 대상 메서드 수 > 0 (스캔이 비어있지 않음 보장)
- [ ] `./gradlew build test` 전체 그린
- [ ] 동작 변경 0 (테스트만 추가)

## 한계 & 비고
- `JpaSpecificationExecutor`의 `findAll(spec, …)`처럼 상속된 제네릭 메서드는 검사 대상이 아니며, 격리는 Specification(`SaleSpecifications.filter`가 `userId` 포함)이 담당 → 행위 기반 per-도메인 통합 테스트가 별도로 커버.
- 이 가드는 "리포지토리 쿼리 표면"의 격리를 보장한다. `findById` 후 서비스에서 소유권 확인하는 load-then-check 패턴은 행위 테스트가 보완.
