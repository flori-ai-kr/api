# 타겟 리팩터링 + 테스트 인프라 강화 — 설계

> 2026-06-11 · 브랜치 `refactor/with-fable5` → `dev` PR 1개 · 모든 단계 동작 보존(behavior-preserving)

## 배경과 진단

전면 리팩터링 요청으로 코드베이스를 전수 탐색한 결과, **구조는 이미 건강**했다:
16개 도메인 전부 `controller → service → repository` 레이어 일관, 멀티테넌시 격리 누락 없음,
N+1 배치 조회로 방어됨, 테스트 94개 파일(소스 대비 42%).

따라서 "갈아엎기"가 아니라 **실제 확인된 문제만 수술하는 타겟 리팩터링 + 테스트 올인**으로 범위를 확정했다(사용자 승인).

### 확인된 문제 4가지

1. **레이어 위반 — 네이티브 SQL이 서비스에 산재.**
   프로젝트 원칙(`controller → service → repository`)과 달리 JdbcTemplate SQL이 서비스 클래스에 직접 박혀 있다.
   - `CustomerService.kt:281-380` — JDBC 쿼리 4개 + 거의 동일한 row mapper 2벌 중복(photoSummaryByCustomer / photoSummaryFor)
   - `SaleService.kt` — summary SQL 문자열 빌딩(appendFilters/appendInClause/SUMMARY_SELECT)이 서비스에
   - statistics 4개 서비스, DashboardService 등 10+ 파일 동일 패턴
   - 결과: 서비스 가독성 저하, SQL 단독 테스트 불가(private 메서드), 중복

2. **서비스 책임 혼재.**
   - `SaleService`(418줄): CRUD + summary SQL 빌딩 + 미수(unpaid) 전이 + 예약 동기화 + 고객 연결 해석
   - `CustomerService`(393줄): `CustomerGradeService`가 이미 존재하는데 등급 로직
     (autoGradeId / recomputeGrade / updateGrade / revertGradeToAuto)이 CustomerService에 남아 분리가 어중간

3. **페이지네이션 비일관.** `offset/limit` 방식과 `page/size` 방식 혼용(6곳), coerce·상한 적용 제각각.

4. **테스트 인프라 빈약.** `support/`에 TestAccounts.kt 1개뿐. 도메인 픽스처 빌더 부재 →
   새 테스트 작성 비용 높음. 네이티브 SQL·Specifications 직접 검증 테스트 부족.

## 설계

### 하드 제약 (전 단계 공통)

- **API 계약 무변경** — 모든 RestDocs 테스트가 수정 없이 통과해야 함(= 계약 불변 증명)
- **DB 스키마 무변경** — `docs/sql/*.sql` 손대지 않음
- **SQL 문장 무변경** — 위치만 이동, 쿼리 텍스트·바인딩·실행 계획 동일
- 단계마다 커밋 + `./gradlew build test` 통과(ktlint·detekt·JaCoCo 80% 게이트 포함)

### 단계 1 — 테스트 안전망 (이후 단계의 회귀 방어선)

`src/test/kotlin/kr/ai/flori/support/`에 도메인 픽스처 빌더 구축:
- `SaleFixtures`, `CustomerFixtures`, `ExpenseFixtures` 등 — 합리적 기본값 + 필요한 것만 오버라이드
- 멀티테넌시 격리 검증 헬퍼 — "다른 테넌트 데이터가 보이면 실패" 패턴 공통화
- 기존 테스트는 수정하지 않음(추가만)

### 단계 2 — SQL을 repository 레이어로 이동 (`{Domain}QueryRepository`)

JdbcTemplate 기반 네이티브 SQL을 `@Repository` 클래스로 추출. SQL·매핑 로직은 그대로 이동.
- `customers/repository/CustomerQueryRepository` — statsFor / aggregateStats / purchaseCounts / photoSummary(중복 mapper 1벌로 통합)
- `sales/repository/SaleSummaryQueryRepository` — SUMMARY_SELECT + appendFilters + appendInClause + 컬럼 화이트리스트
- dashboard·statistics 등 나머지 도메인도 동일 패턴 적용 검토(이미 서비스 분리가 잘 된 곳은 SQL 이동만)

### 단계 3 — 서비스 책임 정리 (기존 분리 패턴 답습)

- 등급 로직(autoGradeId / recomputeGrade / updateGrade / revertGradeToAuto)을 `CustomerGradeService`로 완전 통합
- `SaleService`에서 미수 전이(completeUnpaid / revertUnpaid / applyUnpaidTransition)를 `SaleUnpaidService`로 추출
- 프로젝트에 이미 있는 분리 선례(CustomerGradeService, ReservationNotificationService)를 그대로 따름

### 단계 4 — 페이지네이션 표준화

`common/`에 변환 헬퍼 1개. API 파라미터 형태(offset/limit, page/size)는 그대로 유지하고
내부 PageRequest 변환·상한(coerce) 검증만 통일.

### 단계 5 — 리포지토리 테스트 추가

단계 2에서 추출한 QueryRepository들과 `SaleSpecifications`를 Zonky embedded PostgreSQL로 직접 테스트.
멀티테넌시 격리 케이스(타 테넌트 데이터 미노출) 필수 포함.

### 단계 6 — 문서화

- `docs/refactoring/26-06-11-{슬러그}.md` — 문제 → 이유 → 개선 형식의 리팩터링 기록(사용자 가독성 우선)
- CLAUDE.md / docs/PATTERNS.md에 QueryRepository 패턴·픽스처 빌더 사용법 반영

## 의도적으로 하지 않는 것

- AI 엔티티 nullable 정리(스키마 변경 리스크 > 효과)
- 헥사고날 등 아키텍처 재설계(현 구조 일관 → 회귀 리스크만 큼)
- Redis 캐싱 등 인프라 추가
- 동작(behavior) 변경 일체

## 완료 기준

- `./gradlew build test` 전체 통과(기존 RestDocs 테스트 무수정 통과)
- CustomerService·SaleService에서 JdbcTemplate 의존 제거
- 추출된 QueryRepository 전부에 직접 테스트 존재
- 리팩터링 기록 문서 작성 완료
- `/feature-finalize`로 PR 1개 생성
