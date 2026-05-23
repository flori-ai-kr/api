# SPEC-SERVER-006 — 지출 + 고정비 API

> status: DOING · deps: 004 · Phase 1 (M2 도메인)

## 목표

지출 도메인 + 고정비(반복 지출) 도메인 REST API. 지출 CRUD/자동완성, 고정비 CRUD/토글/빠른추가,
iOS 스타일 "이것만/이후 모두" 분기, `@Scheduled` KST 00:30 고정비 자동생성(멱등, skip 고려).

## 범위 (In)

- **지출**: `Expense` 엔티티 + CRUD + 월 필터 + 자동완성(물품명/거래처/비고 빈도순). `total_amount = unit_price*quantity` 서버 계산(SSOT).
- **고정비 템플릿**: `RecurringExpense`(다중값 규칙: days_of_week INT[], days_of_month INT[], yearly_dates jsonb) + CRUD + 활성 토글 + 빠른추가(오늘 즉시 지출, recurring_id 미연결).
- **this/all 분기**(인스턴스 기준): 이것만 수정(인스턴스만, is_recurring_modified), 이후 모두 수정(템플릿+인스턴스), 이것만 삭제(skip 마커), 이후 모두 삭제(템플릿 end_date 단축).
- **자동생성**: `RecurringScheduleEvaluator`(발생 판정 순수 로직) + `RecurringExpenseGenerator`(active·due·skip 제외 → expenses 멱등 INSERT, `ON CONFLICT (recurring_id,date)`). `@Scheduled(cron 0 30 0, KST)`.
- 모든 테넌트 쿼리 `TenantContext.currentUserId()` 격리. 자동생성은 시스템 작업(전체 테넌트, user_id 복사).

## 범위 밖 (Out)

- 지출 통계/요약 (→ SPEC-013).
- 다음 발생일(nextOccurrence) 표시 헬퍼(앱 표시용, 후속).
- 고정비 카드수수료 계산(고정비는 입금대조 비대상).

## 인수 기준

1. `./gradlew build test` 통과(배열/jsonb 컬럼 validate 포함).
2. 지출 총액 = 단가*수량 서버 계산, 수정 시 재계산.
3. 지출 월 필터·자동완성(빈도순) 동작.
4. 고정비 CRUD/토글/빠른추가(빠른추가는 recurring_id 미연결) 동작.
5. 발생 판정: 주(요일·격주)/월(지정일·말일 클램핑)/연(월일) + 시작전/종료후 제외.
6. 자동생성: 발생일에 1건 생성, **중복 실행 멱등(1건 유지)**, skip 존재 시 미생성, 비발생일 미생성.
7. this/all 분기 4종 정확 동작(인스턴스/템플릿 영향 범위).
8. **멀티테넌시 격리**: 다른 user의 지출/고정비 조회·수정 차단.

## 설계 메모

- 배열/jsonb는 **Hibernate 6 네이티브 `@JdbcTypeCode(ARRAY/JSON)`** 매핑(ddl-auto=validate 친화적). DESIGN의 hypersistence 권장 대비 동등하며 validate 안정성 우수.
- 발생 판정은 원본 cron `isDueToday` 규칙 이식(요일 ISO→일0~토6 변환, 격주=시작 기준 주차, 말일 클램핑).
- 자동생성 멱등성은 DB `(recurring_id,date)` UNIQUE + `ON CONFLICT DO NOTHING`(JdbcTemplate)으로 보장. 스케줄 트리거와 생성 로직 분리(테스트는 generateForDate 직접 호출).
- 검증: Zonky 임베디드 PG. 패턴은 SPEC-005 매출 구조 동일.
