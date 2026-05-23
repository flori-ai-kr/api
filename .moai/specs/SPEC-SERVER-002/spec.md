# SPEC-SERVER-002 — DB + Flyway baseline

> status: DOING · deps: 001 · Phase 1 (M1 기반)

## 목표

원본 Hazel(Supabase) 스키마를 자체 PostgreSQL로 이식하고 Flyway로 버전 관리한다.
Supabase 고유 요소(RLS, `auth.users` FK)를 제거하고 자체 `users` 테이블을 도입해, 이후 도메인 SPEC이 엔티티/리포지토리를 올릴 수 있는 DB 토대를 만든다.

## 범위 (In)

- **데이터 접근 의존성**: `spring-boot-starter-data-jpa`, PostgreSQL 드라이버, Flyway(core + postgresql), hypersistence-utils(jsonb/배열 매핑, 도메인 SPEC 대비).
- **연결 설정**: `application.yml` datasource/JPA/Flyway 전부 `${ENV}` 참조. `ddl-auto=none`(스키마 SSOT는 Flyway), `open-in-view=false`.
- **Flyway baseline (`V1__init_schema.sql`)**: 원본 schema.sql + 후속 마이그레이션의 **최종 상태**를 이식.
  - RLS/정책 전부 제거(멀티테넌시는 애플리케이션이 강제).
  - `auth.users` FK 제거 → 자체 `users` 테이블 추가, 모든 `user_id`가 `users(id)` 참조(`ON DELETE CASCADE`).
  - jsonb/배열/uuid/timestamptz 타입 유지, 복합 unique 제약 유지.
  - 드리프트 반영: `sales.is_unpaid`, `reservations.reminder_sent`/`pickup_completed`, `recurring_expenses` 다중값 컬럼(`days_of_week`/`days_of_month`/`yearly_dates`), `expenses.category` CHECK 제거, `calendar_events` 테이블.
- **시드 (`V2__seed_instagram_accounts.sql`)**: 공유 인사이트 계정 15개(테넌트 무관 참조 데이터).
- **검증 테스트**: 실제 PostgreSQL(Zonky 임베디드, Docker 불필요)에 마이그레이션 적용 → 테이블/시드/FK/무RLS 확인 + DB 무관 SQL 규칙 테스트.

## 범위 밖 (Out)

- JPA 엔티티/리포지토리 (→ 각 도메인 SPEC). 이 SPEC은 엔티티 0개, ddl-auto=none.
- refresh token 등 인증 저장소 (→ SPEC-003).
- 통계용 RPC(`get_sales_summary`) 함수 이식 — 통계는 SPEC-013에서 네이티브 SQL로 재구현하므로 함수는 이식하지 않음.

## 인수 기준

1. `./gradlew build test` 통과.
2. Flyway `V1`/`V2`가 실제 PostgreSQL에 오류 없이 적용된다(Zonky 임베디드로 테스트에서 실제 실행).
3. 22개 테이블(자체 `users` + 이식 21개)이 생성된다.
4. `pg_policies`에 RLS 정책이 0건(완전 제거 확인).
5. 임의 `user_id`(users에 없는 값)로 `customers` INSERT 시 FK 위반으로 실패한다.
6. `instagram_accounts` 시드 15건 적재.
7. `application.yml`에 평문 시크릿 없음(전부 `${ENV}`).

## 설계 메모

- **테스트 DB 전략**: 환경에 Docker 데몬이 없어 Testcontainers 대신 **Zonky embedded-postgres**(실제 PG 바이너리, Docker 불필요)를 채택. CI/로컬 동일하게 실제 Postgres에서 검증. (DESIGN의 Testcontainers 권장 대비 동등한 "실제 PG" 보장 + 무Docker 이식성.)
- updated_at은 원본과 동일하게 DB 트리거(`update_updated_at`)로 유지 — raw SQL 경로에서도 일관.
- 통계 RPC는 의도적으로 미이식(SPEC-013에서 네이티브 쿼리로 대체).
- payment_method CHECK: sales는 `unpaid` 포함(미수), expenses는 원본대로 `unpaid` 미포함.
