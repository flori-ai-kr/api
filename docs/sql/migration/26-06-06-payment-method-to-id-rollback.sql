-- 26-06-06-payment-method-to-id.sql 롤백.
-- payment_method_id → value 문자열 복원. payment_method_id=NULL(미수)는 'unpaid' 로 복원.

BEGIN;

-- ===== sales =====
ALTER TABLE sales ADD COLUMN payment_method VARCHAR(20);
UPDATE sales s SET payment_method =
  COALESCE((SELECT l.value FROM label_settings l WHERE l.id = s.payment_method_id), 'unpaid');
DROP INDEX IF EXISTS idx_sales_payment_method_id;
ALTER TABLE sales DROP COLUMN payment_method_id;
ALTER TABLE sales ALTER COLUMN payment_method SET NOT NULL;
CREATE INDEX idx_sales_payment_method ON sales(payment_method);

-- ===== expenses =====
ALTER TABLE expenses ADD COLUMN payment_method VARCHAR(20);
UPDATE expenses e SET payment_method = (SELECT l.value FROM label_settings l WHERE l.id = e.payment_method_id);
ALTER TABLE expenses DROP COLUMN payment_method_id;

-- ===== recurring_expenses =====
ALTER TABLE recurring_expenses ADD COLUMN payment_method VARCHAR(20);
UPDATE recurring_expenses r SET payment_method = (SELECT l.value FROM label_settings l WHERE l.id = r.payment_method_id);
ALTER TABLE recurring_expenses DROP COLUMN payment_method_id;

COMMIT;
