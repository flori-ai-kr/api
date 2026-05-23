# SPEC-SERVER-009 — 입금대조 API

> status: DOING · deps: 005 · Phase 1 (M2 도메인)

## 목표

카드 매출의 입금 대조 기능. 입금 목록 조회(상태/카드사/월 필터), 단건·다건 입금 확인, 되돌리기, 입금 요약.
sales 테이블을 다루며(별도 테이블 없음) 모든 쿼리를 user_id로 격리한다.

## 범위 (In)

- **엔티티 보강**: `Sale`에 `deposited_at` 매핑 추가, `SaleResponse`에 `depositedAt` 노출.
- **목록**: `GET /deposits` — 카드 매출(`payment_method='card'`) + `status`(pending/completed/all)·`cardCompany`·`month` 필터, 날짜 내림차순. `DepositSpecifications`로 동적 필터.
- **확인**: 단건 `POST /deposits/{id}/confirm`(completed + deposited_at), 다건 `POST /deposits/confirm`(본인 매출만 일괄, 타 테넌트 ID 무시).
- **되돌리기**: `POST /deposits/{id}/revert`(pending + deposited_at null).
- **요약**: `GET /deposits/summary` — 대기/완료 건수·금액(예상입금액 = expected_deposit ?: amount).
- 모든 쿼리 `TenantContext.currentUserId()` 격리(HARD).

## 범위 밖 (Out)

- 대시보드 전체 통계 (→ SPEC-013). 여기 요약은 입금대조 화면 전용(카드 한정).
- 입금 자동 매칭/은행 연동.

## 인수 기준

1. `./gradlew build test` 통과(`deposited_at` validate 포함).
2. 목록: 카드 매출만, status/cardCompany/month 필터.
3. 확인: completed + deposited_at 기록. 되돌리기: pending + deposited_at 제거.
4. 다건 확인: 본인 매출 일괄 완료, 타 테넌트 ID 무시.
5. 요약: 대기/완료 건수·금액(예상입금액) 정확.
6. **멀티테넌시 격리**: 다른 user의 입금 확인/목록 차단.

## 설계 메모

- 입금대조는 sales를 직접 다루므로 `Sale`/`SaleRepository`/`SaleResponse`를 재사용·확장(별도 엔티티 없음).
- 다건 확인은 `findByUserIdAndIdIn`으로 본인 소유만 선별 → 타 테넌트 ID 자동 무시(격리).
- 카드 매출은 SPEC-005에서 생성 시 deposit_status=pending + expected_deposit 계산됨. 본 SPEC은 그 상태 전이만 담당.
- 검증은 Zonky 임베디드 PG(SaleService로 카드 매출 생성 후 대조).
