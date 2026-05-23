# SPEC-SERVER-007 — 고객 API

> status: DOING · deps: 004 · Phase 1 (M2 도메인)

## 목표

고객 도메인 REST API. CRUD + 등급/성별, 전화번호+계정 복합키 기반 findOrCreate(매출 연결용),
고객별 매출 조회, 이름 검색, 전화번호 중복 확인. 구매 통계는 매출에서 실시간 집계.

## 범위 (In)

- **엔티티/리포지토리**: `Customer`(customers 매핑). 통계 컬럼은 미매핑(실시간 집계).
- **CRUD**: 생성(중복 전화 409)/조회/부분수정/삭제. 등급 변경 전용 엔드포인트.
- **등급/성별 검증**: grade ∈ {new,regular,vip,blacklist}, gender ∈ {male,female}|null.
- **findOrCreate**: `(phone, user_id)` 복합 unique 기준 찾기/생성(레이스 시 재조회).
- **고객별 매출**: `GET /customers/{id}/sales` 페이지네이션(소유권 확인 후).
- **검색/중복**: 이름 부분검색(top 10), 전화번호 중복 확인(있으면 고객, 없으면 204).
- **구매 통계**: sales 실시간 집계(count/sum/min·max date) — 목록은 group by 1쿼리, 단건은 단일 집계. 목록은 총구매액 내림차순.
- 모든 쿼리 `TenantContext.currentUserId()` 격리(HARD).

## 범위 밖 (Out)

- 매출 생성 시 고객 자동연결(findOrCreate 호출)을 sales에 통합 — 후속(현재 sales는 customer_id 소유권만 검증). findOrCreate API는 제공.
- 고객 통계 대시보드 (→ SPEC-013).

## 인수 기준

1. `./gradlew build test` 통과.
2. 생성: 기본 등급 new, 중복 전화번호 409.
3. 등급 변경/부분 수정 동작, 잘못된 등급/성별 400.
4. findOrCreate: 같은 전화번호면 기존 반환, 없으면 생성.
5. 구매 통계가 매출에서 정확히 집계(횟수/총액/최초·최근일), 고객별 매출 페이지네이션.
6. 이름 부분검색, 전화번호 중복 확인.
7. **멀티테넌시 격리**: 다른 user의 고객 조회/목록 차단.

## 설계 메모

- 통계는 sales 실시간 집계(JdbcTemplate 네이티브, DESIGN의 통계=네이티브 SQL 방침). 저장 컬럼(total_purchase_*)은 미사용·미매핑.
- 고객별 매출은 `SaleRepository.findByUserIdAndCustomerId`(테넌트 격리) → `SaleResponse` 재사용(읽기 전용 도메인 간 결합).
- findOrCreate는 find→insert, 동시 생성 레이스는 `DataIntegrityViolationException` 포착 후 재조회.
- 패턴은 sales/expenses 동일. 검증은 Zonky 임베디드 PG(서비스 + HTTP).
