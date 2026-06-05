-- 26-06-06-category-channel-to-id.sql 롤백.
-- id 참조를 다시 value 문자열 컬럼으로 복원한다(label_settings.value 기준).
-- 자동 생성된 레거시 라벨 행은 label_settings 에 남는다(무해).

BEGIN;

-- ===== sales =====
ALTER TABLE sales ADD COLUMN product_category VARCHAR(100);
ALTER TABLE sales ADD COLUMN reservation_channel VARCHAR(20) DEFAULT 'other';

UPDATE sales s SET product_category = l.value
FROM label_settings l WHERE l.id = s.category_id;

UPDATE sales s SET reservation_channel =
  COALESCE((SELECT l.value FROM label_settings l WHERE l.id = s.channel_id), 'other');

DROP INDEX IF EXISTS idx_sales_category_id;
DROP INDEX IF EXISTS idx_sales_channel_id;
ALTER TABLE sales DROP COLUMN category_id;
ALTER TABLE sales DROP COLUMN channel_id;

-- ===== expenses =====
ALTER TABLE expenses ADD COLUMN category VARCHAR(30);
UPDATE expenses e SET category = l.value
FROM label_settings l WHERE l.id = e.category_id;
DROP INDEX IF EXISTS idx_expenses_category_id;
ALTER TABLE expenses DROP COLUMN category_id;
CREATE INDEX idx_expenses_category ON expenses(category);

-- ===== recurring_expenses =====
ALTER TABLE recurring_expenses ADD COLUMN category VARCHAR(30);
UPDATE recurring_expenses r SET category = l.value
FROM label_settings l WHERE l.id = r.category_id;
ALTER TABLE recurring_expenses DROP COLUMN category_id;

COMMIT;
