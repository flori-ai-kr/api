-- 입금대조(deposit reconciliation) + 카드사별 수수료(per-card-company fee) 기능 제거
-- 및 미사용(미매핑) 컬럼/테이블 정리. 웹 제품에서 해당 기능이 이미 제거되어 서버를 일치시킨다.
-- 주의: 미수(unpaid: is_unpaid / payment_method='unpaid')와 결제방식 'card'는 별개 기능이라 유지한다.
-- 순서: 인덱스 → 컬럼 → 테이블.

-- 1) 인덱스 제거 (입금상태 인덱스)
DROP INDEX IF EXISTS idx_sales_deposit_status;

-- 2) 미사용(미매핑) 컬럼 제거
ALTER TABLE sales DROP COLUMN IF EXISTS reservation_id;
ALTER TABLE sales DROP COLUMN IF EXISTS photos;
ALTER TABLE customers DROP COLUMN IF EXISTS total_purchase_count;
ALTER TABLE customers DROP COLUMN IF EXISTS total_purchase_amount;
ALTER TABLE customers DROP COLUMN IF EXISTS first_purchase_date;
ALTER TABLE customers DROP COLUMN IF EXISTS last_purchase_date;

-- 3) 입금대조 / 카드 수수료 컬럼 제거 (sales)
--    deposit_status의 CHECK 제약, reservation_id의 FK 등 의존 객체는 컬럼 삭제 시 함께 제거된다.
ALTER TABLE sales DROP COLUMN IF EXISTS fee;
ALTER TABLE sales DROP COLUMN IF EXISTS expected_deposit;
ALTER TABLE sales DROP COLUMN IF EXISTS expected_deposit_date;
ALTER TABLE sales DROP COLUMN IF EXISTS deposit_status;
ALTER TABLE sales DROP COLUMN IF EXISTS deposited_at;
ALTER TABLE sales DROP COLUMN IF EXISTS card_company;

-- 4) 미사용 테이블 / 카드사 설정 테이블 제거
--    card_company_settings의 update 트리거·인덱스는 테이블 삭제 시 함께 제거된다.
DROP TABLE IF EXISTS card_company_settings;
DROP TABLE IF EXISTS app_config;
