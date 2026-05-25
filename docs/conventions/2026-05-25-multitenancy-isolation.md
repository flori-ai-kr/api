# 멀티테넌시 격리 (user_id)

> 2026-05-25 · 상태: 적용 중 · 관련 SPEC: SERVER-003/004/RF-001/016

## 배경/맥락

원본 Supabase 웹앱은 PostgreSQL **RLS**(`auth.uid() = user_id`)로 테넌트(사용자)별 데이터를 격리했다. 이 백엔드는 RDS PostgreSQL을 RLS 없이 쓰므로 **애플리케이션이 유일한 방어선**이다. `user_id` 필터 누락은 곧 다른 사용자의 데이터 유출(IDOR)로 직결된다.

## 결정 (Best Practice)

1. **요청 스코프 식별**: JWT `sub` → `JwtAuthenticationFilter` → `TenantContext`(요청 스코프)에 `userId` 저장. 서비스는 `TenantContext.currentUserId()`로만 테넌트를 얻는다(요청 바디의 userId 신뢰 금지).
2. **리포지토리 격리**: 모든 도메인 조회/변경은 `...AndUserId` 파생 쿼리 또는 `@Query`의 `user_id` 조건으로 격리한다. 네이티브 SQL도 `user_id = ?` 바인딩 필수.
3. **load-then-check**: 상속된 `findById` 등으로 로드한 뒤 서비스에서 소유권(`userId` 일치)을 확인하는 패턴도 허용하되, 가능한 한 리포지토리 레벨에서 격리한다. 다른 엔티티의 FK(`saleId` 등)는 그대로 신뢰하지 말고 소유 여부를 먼저 검증한다.
4. **의도적 전역은 명시**: 인증(사용자/토큰 조회), 스케줄러(전체 테넌트 시스템 작업), 공유 콘텐츠(인사이트 트렌드/인스타) 등 비격리 쿼리는 **자동 가드 화이트리스트에 사유와 함께** 등록한다.
5. **자동 가드**: `TenantIsolationGuardTest`가 모든 `kr.ai.flori` 리포지토리의 선언 메서드를 리플렉션으로 전수 검사 — 격리(메서드명 `UserId` 또는 `@Query` `user_id`)되거나 화이트리스트에 있어야 통과. 새 메서드가 `user_id`를 빠뜨리면 테스트 실패.
6. **복합 unique**: `(phone, user_id)`, `(value, user_id)`, `(user_id, target_type, target_id)` 등 테넌트 스코프 unique 제약을 유지한다.

## 근거 (Rationale)

- RLS가 없으므로 격리를 코드에 위임할 수밖에 없고, 사람의 주의력에만 의존하면 회귀가 불가피하다 → **테스트로 강제**(가드)해야 안전하다.
- 화이트리스트 자기검증(항목이 실재 + 실제 비격리)으로 "전역이 맞다"는 판단을 문서가 아닌 코드로 박제 → stale 방지.
- 대안인 Hibernate `@Filter` 전역 활성화도 검토했으나, 명시적 `...AndUserId` 쿼리가 가독성·정적 검사 친화성에서 우위라 채택하지 않음(과한 마법 회피).

## 공식 문서/참고

- OWASP Top 10 — A01:2021 Broken Access Control (IDOR): https://owasp.org/Top10/A01_2021-Broken_Access_Control/
- Spring Data JPA — Query Methods: https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html
- (대안 검토) Hibernate ORM — Filters: https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html#pc-filtering

## 적용 범위·예외

- 적용: 모든 도메인 리포지토리/서비스.
- 예외(화이트리스트): `UserRepository`(email 조회)·`RefreshTokenRepository`(토큰 해시)·스케줄러 전역 쿼리·인사이트 공유 콘텐츠 리포지토리. 변경 시 `TenantIsolationGuardTest.intentionalGlobal` 동기화.
