-- photo_cards 에 customer_id soft 참조 추가(FK 없음). 기존 sale 경유 연결분 백필.
BEGIN;

ALTER TABLE photo_cards ADD COLUMN IF NOT EXISTS customer_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_photo_cards_user_customer
  ON photo_cards(user_id, customer_id);

-- 백필: sale_id가 있고 그 sale에 customer_id가 있는 카드는 그 고객으로 연결
UPDATE photo_cards pc
SET customer_id = s.customer_id
FROM sales s
WHERE pc.sale_id = s.id
  AND pc.customer_id IS NULL
  AND s.customer_id IS NOT NULL;

COMMIT;
