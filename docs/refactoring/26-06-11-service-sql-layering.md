# 타겟 리팩터링: 서비스 레이어 SQL 정리 + 테스트 인프라 강화

> 2026-06-11 · 브랜치 `refactor/with-fable5` · 설계: `docs/superpowers/specs/2026-06-11-targeted-refactoring-design.md`

## 한 줄 요약

"전면 갈아엎기" 대신, 전수 탐색으로 **실제 확인된 문제 4가지만 수술**했다. 모든 변경은 동작 보존
(API 계약·DB 스키마·SQL 문장 무변경)이며, 그 증명으로 기존 RestDocs/통합 테스트가 무수정 통과하고
재생성한 OpenAPI 스펙이 스키마 수준에서 완전 동일함을 확인했다. 부수입으로 **실제 버그 1건**을 발견·수정했다.

## 왜 전면 재설계를 안 했나

리팩터링 전 전수 탐색 결과, 이 코드베이스는 이미 건강했다: 16개 도메인 전부
`controller → service → repository` 레이어 일관, 멀티테넌시(user_id) 격리 누락 없음, N+1 배치 조회로
방어됨, 테스트 94개 파일. 이런 상태에서 구조를 갈아엎으면 **얻는 것보다 동작하는 서비스를 깨뜨릴
리스크가 크다.** 그래서 범위를 "확인된 문제 + 테스트 올인"으로 좁혔다.

---

## 문제 1 — 네이티브 SQL이 서비스 레이어에 박혀 있었다

**무엇이 문제였나.** CustomerService(393줄)·SaleService(418줄)·ExpenseService(257줄) 안에
JdbcTemplate SQL 문자열과 결과 매핑 코드가 직접 들어 있었다. CustomerService는 393줄 중 약 120줄이
SQL이었고, 썸네일 매핑 코드가 거의 복붙으로 2벌 존재했다.

**왜 문제인가.**
- 프로젝트 스스로 선언한 원칙(`controller → service → repository`) 위반 — SQL은 데이터 접근이므로 repository 소속
- 비즈니스 로직을 읽으러 온 사람(과 AI 에이전트)이 SQL 사이를 헤매게 됨
- 서비스의 private 메서드라서 **SQL을 단독으로 테스트할 방법이 없었음**

**어떻게 바꿨나.** SQL과 매핑을 `{Domain}QueryRepository`(@Repository, JdbcTemplate)로 그대로 이동.
SQL 문장은 한 글자도 바꾸지 않았다 — 위치만 옮겼다.

| 신규 클래스 | 가져온 것 | 결과 |
|---|---|---|
| `customers/repository/CustomerQueryRepository` | 구매통계·고객별집계·구매건수·사진요약 SQL 4종 | 복붙 2벌 매퍼 → `mapThumbnails` 1벌 |
| `sales/repository/SaleSummaryQueryRepository` | SUMMARY_SELECT + 동적 WHERE 빌딩 + 컬럼 화이트리스트 | SaleService.summary는 위임 1줄 |
| `expenses/repository/ExpenseSummaryQueryRepository` | 총액·카테고리별 합계 SQL + 동적 WHERE 빌딩 | ExpenseService 257줄 → 175줄 |

각 QueryRepository에 임베디드 PostgreSQL 직접 테스트를 새로 붙였다(테넌트 격리 케이스 필수 포함).

**의도적으로 안 옮긴 것.** statistics 4개 서비스와 DashboardService도 JdbcTemplate을 쓰지만 제외했다.
이들은 비즈니스 로직 없이 SQL+DTO 조립만 하는 읽기 전용 클래스라 이미 사실상 query-object다.
옮기면 레이어만 하나 늘고 얻는 게 없다. 문제는 SQL의 존재가 아니라 **비즈니스 로직과의 혼재**였다.

## 문제 2 — 서비스 책임 혼재

**무엇이 문제였나.**
- 등급 정책(autoGradeId/recomputeGrade)이 `CustomerGradeService`가 따로 있는데도 CustomerService에 남아 있었다 — 분리가 어중간한 상태
- SaleService는 매출 CRUD 외에 미수(외상) 전이·응답 조립·고객 연결 해석까지 들고 있었다

**어떻게 바꿨나.** 프로젝트에 이미 있던 분리 선례(CustomerGradeService, ReservationNotificationService)를 그대로 따랐다.

| 변경 | 내용 |
|---|---|
| 등급 정책 → `CustomerGradeService` | "구매횟수 N이면 어떤 등급인가"(gradeIdFor)와 자동 재계산(recomputeGrade)의 단일 소유자. SaleService도 이제 CustomerGradeService를 직접 호출 |
| `sales/service/SaleUnpaidService` | 미수 완료/되돌리기/수정 시 전이 규칙의 단일 소유자 |
| `sales/service/SaleResponseAssembler` | 라벨(id→이름) 해석 + SaleResponse 조립 — SaleService·SaleUnpaidService 공유 |
| `sales/service/SaleCustomerLinker` | 매출↔고객 연결(소유권 검증·전화번호 findOrCreate) — 고객 도메인 접근을 한곳에 |

결과: SaleService 418줄 → **226줄**, 매출 CRUD·목록·예약 동기화에만 집중.
(SaleCustomerLinker 분리는 detekt의 생성자 파라미터 상한 경고가 직접 트리거 — 의존이 10개를 넘는 것 자체가 책임 과다 신호였다.)

## 문제 3 — 페이지네이션 보정이 제각각

**무엇이 문제였나.** `MIN_LIMIT/MAX_LIMIT + coerce`가 컨트롤러 3곳에 복붙, `PageRequest.of(offset / limit, ...)`
변환이 서비스 3곳에 복붙, page/size 방식 보정이 또 3곳에 제각각.

**어떻게 바꿨나.** `common/util/Paging`(offsetLimit/pageSize) 하나로 통일. API 파라미터 계약과 보정 결과는
무변경이고, 상한값(100/50/200)은 각 서비스가 그대로 소유한다. 보정 규칙은 순수 단위 테스트(PagingTest)로 고정.

## 문제 4 — 테스트 인프라 빈약

**무엇이 문제였나.** `support/`에 TestAccounts 1개뿐. `newTenant()` 부트스트랩이 **테스트 22개 파일에 복붙**,
라벨 조회 헬퍼가 14곳 복붙. 새 테스트를 쓸 때마다 보일러플레이트를 또 복사해야 했다.

**어떻게 바꿨나.**
- `support/TestTenants` — 가입→userId→TenantContext 설정 한 줄(bootstrap), 테넌트 전환(runAs)
- `support/Fixtures` — Sale/Customer/Expense 엔티티 빌더(합리적 기본값) + 시드 라벨 id 조회(labelId)
- 신규 리포지토리 테스트 6파일이 모두 이 헬퍼로 작성됨

기존 22개 파일의 복붙은 이번에 일괄 수정하지 않았다(PR 비대화 방지) — **신규 테스트부터 헬퍼 사용**이
규칙이고, 기존 파일은 만질 일이 있을 때 점진 전환한다.

---

## 보너스: 새 테스트가 실제 버그를 찾았다

SaleSpecifications 직접 테스트를 작성하자 `%` 포함 검색 케이스가 실패했다. 원인: **Hibernate 6의
criteria `like`는 ESCAPE 절을 명시하지 않으면 이스케이프가 무효** — 코드는 `%`→`\%`로 이스케이프해서
패턴을 만들었지만 DB에서 `\`가 리터럴로 취급돼, `%`나 `_`가 들어간 검색어는 목록(GET /sales)에서
아무것도 못 찾았다. 반면 summary(JDBC 직접 쿼리)는 정상 매칭 → **같은 검색어에 목록과 요약이 다른 결과**를
내는 상태였다.

두 코드 모두 "동일 필터 규약"을 주석으로 명시하고 있었으므로, Specification에 escape 문자를 명시해
계약대로 정합시켰다(`SaleSpecifications`, `ExpenseSpecifications`). 목록·summary 건수 일치를 교차
검증하는 테스트로 회귀를 방지한다.

> 교훈: "쿼리를 단독으로 테스트할 수 없다"는 구조 문제를 풀자마자 숨어 있던 동작 불일치가 드러났다.
> QueryRepository 분리의 가치가 바로 이것이다.

## 무결성 증명 (전 단계 공통)

- 단계마다 `./gradlew build test` 통과 후 커밋 (ktlint + detekt + 전체 테스트 + JaCoCo 80% 게이트)
- 기존 RestDocs·API 통합 테스트 전부 **무수정** 통과 = 엔드포인트 계약 불변
- `./gradlew openapi3` 재생성 후 신구 스펙 비교: 차이 97건 전부 examples(테스트 랜덤 값)뿐, **스키마·경로·파라미터 완전 동일**
- 신규 테스트 6파일 추가 (QueryRepository 3 + Specifications 2 + Paging 1, 테넌트 격리 케이스 전부 포함)

## 하지 않은 것 (후속 과제)

- 기존 테스트 22개 파일의 newTenant() 복붙 → TestTenants로 점진 전환
- statistics/dashboard 서비스의 SQL — 현 구조가 적절해 유지 (위 "의도적으로 안 옮긴 것" 참조)
- AI 엔티티 nullable 정리 — 스키마 변경 리스크 대비 효과 낮음
- 캐싱·인프라 변경 일체
