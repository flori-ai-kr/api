# SPEC-SERVER-005 — 매출 API

> status: DOING · deps: 004 · Phase 1 (M2 도메인) · **첫 도메인 SPEC(패턴 기준점)**

## 목표

매출 도메인 REST API. 엔티티/리포지토리/서비스/컨트롤러 레이어와 멀티테넌시 격리 패턴을 확립한다.
카드수수료·입금예정일·입금상태를 서버가 계산(SSOT)하고, 무한스크롤·다중선택 필터·자동완성·미수 처리를 제공한다.

## 범위 (In)

- **엔티티/리포지토리**: `Sale`(sales 테이블 매핑), `SaleRepository`(JpaSpecificationExecutor) + 동적 필터 `SaleSpecifications`.
- **CRUD**: 생성/단건조회/부분수정/삭제. 모든 쿼리 `TenantContext.currentUserId()`로 격리(HARD).
- **무한스크롤**: `GET /sales?offset&limit`(date desc, created_at desc) → `{sales, hasMore}`.
- **다중선택 필터**: category/payment/channel(`IN`) + month(연/월/일) + search(ILIKE product_category/name/customer_name).
- **서버 계산(SSOT)**: 카드 결제 시 사용자 `card_company_settings`(fee_rate, deposit_days)로
  `fee = round(amount*fee_rate/100)`, `expected_deposit = amount-fee`, `expected_deposit_date = date + N영업일(주말 제외)`, `deposit_status=pending`. 비카드 → not_applicable.
- **미수**: `payment_method='unpaid'` → `is_unpaid=true`(영구 마커). 완료(`/complete-unpaid`)·되돌리기(`/revert-unpaid`).
- **자동완성**: `GET /sales/suggestions` — 비고 빈도순.
- 결제방식/고객 소유권 검증(고객 id 제공 시 테넌트 소속 확인).

## 범위 밖 (Out)

- 매출 요약/통계 집계 (→ SPEC-013, 네이티브 SQL).
- 사진 업로드/삭제 (→ SPEC-010). `photos` 컬럼 미매핑.
- 고객 자동생성(findOrCreate) (→ SPEC-007). 여기서는 customer_id 전달 시 소유권만 검증, 이름/전화는 비정규화 저장.
- 예약 연동(reservation_id), 입금대조 완료(deposited_at) (→ SPEC-008/009).

## 인수 기준

1. `./gradlew build test` 통과(엔티티-스키마 validate 포함).
2. 카드 매출: fee/expected_deposit/expected_deposit_date/deposit_status를 서버가 정확히 계산.
3. 영업일 계산이 주말을 건너뛴다. 수수료는 HALF_UP 반올림.
4. 비카드(현금/이체 등) → not_applicable, fee null. 미수 → is_unpaid=true.
5. 미수 완료/되돌리기 동작(완료 후 is_unpaid 마커 유지).
6. 무한스크롤(hasMore) + 다중선택 필터(category/payment/channel) + 검색 동작.
7. 비고 자동완성 빈도순.
8. **멀티테넌시 격리**: 다른 user의 매출 조회/수정/목록 노출 차단(서비스·HTTP 양쪽 검증).
9. 보호 엔드포인트(토큰 필요), 잘못된 입력 400.

## 설계 메모

- 입금 계산 순수 함수(`DepositMath`: computeFee/addBusinessDays)는 단위 테스트, 카드설정 조회는 `DepositCalculator`(JdbcTemplate, 설정 도메인 엔티티 미생성 회피).
- `is_unpaid`는 생성 시에만 결정되는 영구 마커. 일반 수정은 변경하지 않고 complete/revert로만 전이(원본 의미 보존).
- 검증은 Zonky 임베디드 PG에서 실제 흐름으로(서비스 + HTTP). 가입 시드의 신한카드(2.0%/3일)로 계산 검증.
