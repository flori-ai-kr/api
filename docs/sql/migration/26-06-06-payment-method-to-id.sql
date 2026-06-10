-- 매출/지출의 결제수단(payment_method)을 value 문자열 → label_settings.id 간접참조로 전환.
--   sales.payment_method → sales.payment_method_id   (미수='unpaid' 행은 NULL + is_unpaid=true 유지)
--   expenses.payment_method → expenses.payment_method_id
--   recurring_expenses.payment_method → recurring_expenses.payment_method_id
-- 전제: 26-06-06-unify-label-settings.sql 적용 완료(label_settings 존재).
-- 미수(unpaid)는 더 이상 결제수단 값이 아니라 payment_method_id=NULL + is_unpaid 불린으로 표현한다.
-- 실행 전 백업 권장.

BEGIN;

-- ===== sales: payment_method_id ('unpaid' 은 라벨화하지 않고 NULL 로 둠) =====
ALTER TABLE sales ADD COLUMN payment_method_id BIGINT;

INSERT INTO label_settings (user_id, domain, kind, value, label, sort_order)
SELECT DISTINCT s.user_id, 'sale', 'payment', s.payment_method, s.payment_method, 900
FROM sales s
WHERE s.payment_method IS NOT NULL AND s.payment_method <> 'unpaid'
  AND NOT EXISTS (
    SELECT 1 FROM label_settings l
    WHERE l.user_id = s.user_id AND l.domain = 'sale' AND l.kind = 'payment' AND l.value = s.payment_method
  );

UPDATE sales s SET payment_method_id = l.id
FROM label_settings l
WHERE l.user_id = s.user_id AND l.domain = 'sale' AND l.kind = 'payment' AND l.value = s.payment_method
  AND s.payment_method <> 'unpaid';

-- 미수 행 정합성 보강(혹시 is_unpaid 누락 시): payment='unpaid' → is_unpaid=true
UPDATE sales SET is_unpaid = TRUE WHERE payment_method = 'unpaid';

DROP INDEX IF EXISTS idx_sales_payment_method;
ALTER TABLE sales DROP COLUMN payment_method;
CREATE INDEX idx_sales_payment_method_id ON sales(payment_method_id);

-- ===== expenses: payment_method_id =====
ALTER TABLE expenses ADD COLUMN payment_method_id BIGINT;

INSERT INTO label_settings (user_id, domain, kind, value, label, sort_order)
SELECT DISTINCT e.user_id, 'expense', 'payment', e.payment_method, e.payment_method, 900
FROM expenses e
WHERE e.payment_method IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM label_settings l
    WHERE l.user_id = e.user_id AND l.domain = 'expense' AND l.kind = 'payment' AND l.value = e.payment_method
  );

UPDATE expenses e SET payment_method_id = l.id
FROM label_settings l
WHERE l.user_id = e.user_id AND l.domain = 'expense' AND l.kind = 'payment' AND l.value = e.payment_method;

ALTER TABLE expenses DROP COLUMN payment_method;

-- ===== recurring_expenses: payment_method_id =====
ALTER TABLE recurring_expenses ADD COLUMN payment_method_id BIGINT;

INSERT INTO label_settings (user_id, domain, kind, value, label, sort_order)
SELECT DISTINCT r.user_id, 'expense', 'payment', r.payment_method, r.payment_method, 900
FROM recurring_expenses r
WHERE r.payment_method IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM label_settings l
    WHERE l.user_id = r.user_id AND l.domain = 'expense' AND l.kind = 'payment' AND l.value = r.payment_method
  );

UPDATE recurring_expenses r SET payment_method_id = l.id
FROM label_settings l
WHERE l.user_id = r.user_id AND l.domain = 'expense' AND l.kind = 'payment' AND l.value = r.payment_method;

ALTER TABLE recurring_expenses DROP COLUMN payment_method;

COMMIT;
