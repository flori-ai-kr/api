-- 매출/지출의 카테고리·채널을 value 문자열 → label_settings.id 간접참조로 전환.
--   sales.product_category → sales.category_id
--   sales.reservation_channel → sales.channel_id
--   expenses.category → expenses.category_id
--   recurring_expenses.category → recurring_expenses.category_id
-- 전제: 26-06-06-unify-label-settings.sql 가 먼저 적용되어 label_settings 가 존재해야 한다.
-- payment_method 는 문자열 유지(후속 단계). 실행 전 백업 권장.

BEGIN;

-- ===== sales: category_id =====
ALTER TABLE sales ADD COLUMN category_id BIGINT;

-- label_settings 에 없는 레거시/커스텀 값은 라벨 자동 생성(label=value) 후 매핑(데이터 손실 0).
INSERT INTO label_settings (user_id, domain, kind, value, label, sort_order)
SELECT DISTINCT s.user_id, 'sale', 'category', s.product_category, s.product_category, 900
FROM sales s
WHERE s.product_category IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM label_settings l
    WHERE l.user_id = s.user_id AND l.domain = 'sale' AND l.kind = 'category' AND l.value = s.product_category
  );

UPDATE sales s SET category_id = l.id
FROM label_settings l
WHERE l.user_id = s.user_id AND l.domain = 'sale' AND l.kind = 'category' AND l.value = s.product_category;

-- ===== sales: channel_id =====
ALTER TABLE sales ADD COLUMN channel_id BIGINT;

INSERT INTO label_settings (user_id, domain, kind, value, label, sort_order)
SELECT DISTINCT s.user_id, 'sale', 'channel', s.reservation_channel, s.reservation_channel, 900
FROM sales s
WHERE s.reservation_channel IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM label_settings l
    WHERE l.user_id = s.user_id AND l.domain = 'sale' AND l.kind = 'channel' AND l.value = s.reservation_channel
  );

UPDATE sales s SET channel_id = l.id
FROM label_settings l
WHERE l.user_id = s.user_id AND l.domain = 'sale' AND l.kind = 'channel' AND l.value = s.reservation_channel;

ALTER TABLE sales DROP COLUMN product_category;
ALTER TABLE sales DROP COLUMN reservation_channel;
CREATE INDEX idx_sales_category_id ON sales(category_id);
CREATE INDEX idx_sales_channel_id ON sales(channel_id);

-- ===== expenses: category_id =====
ALTER TABLE expenses ADD COLUMN category_id BIGINT;

INSERT INTO label_settings (user_id, domain, kind, value, label, sort_order)
SELECT DISTINCT e.user_id, 'expense', 'category', e.category, e.category, 900
FROM expenses e
WHERE e.category IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM label_settings l
    WHERE l.user_id = e.user_id AND l.domain = 'expense' AND l.kind = 'category' AND l.value = e.category
  );

UPDATE expenses e SET category_id = l.id
FROM label_settings l
WHERE l.user_id = e.user_id AND l.domain = 'expense' AND l.kind = 'category' AND l.value = e.category;

DROP INDEX IF EXISTS idx_expenses_category;
ALTER TABLE expenses DROP COLUMN category;
CREATE INDEX idx_expenses_category_id ON expenses(category_id);

-- ===== recurring_expenses: category_id =====
ALTER TABLE recurring_expenses ADD COLUMN category_id BIGINT;

INSERT INTO label_settings (user_id, domain, kind, value, label, sort_order)
SELECT DISTINCT r.user_id, 'expense', 'category', r.category, r.category, 900
FROM recurring_expenses r
WHERE r.category IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM label_settings l
    WHERE l.user_id = r.user_id AND l.domain = 'expense' AND l.kind = 'category' AND l.value = r.category
  );

UPDATE recurring_expenses r SET category_id = l.id
FROM label_settings l
WHERE l.user_id = r.user_id AND l.domain = 'expense' AND l.kind = 'category' AND l.value = r.category;

ALTER TABLE recurring_expenses DROP COLUMN category;

COMMIT;
