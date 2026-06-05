-- user_preferences를 (user_id PK, bottom_nav_items) → key-value 스토어로 재구성.
--   (user_id, key, value jsonb, created_at, updated_at), PK(user_id, key).
-- 기존 bottom_nav_items는 key='bottom_nav' 한 행으로 이관. 실행 전 백업 권장.

BEGIN;

ALTER TABLE user_preferences ADD COLUMN key VARCHAR(40);
ALTER TABLE user_preferences ADD COLUMN value JSONB;
ALTER TABLE user_preferences ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- 기존 하단바 설정 이관
UPDATE user_preferences SET key = 'bottom_nav', value = bottom_nav_items;

-- PK 재구성 + 옛 컬럼 제거
ALTER TABLE user_preferences DROP CONSTRAINT user_preferences_pkey;
ALTER TABLE user_preferences DROP COLUMN bottom_nav_items;
ALTER TABLE user_preferences ALTER COLUMN key SET NOT NULL;
ALTER TABLE user_preferences ALTER COLUMN value SET NOT NULL;
ALTER TABLE user_preferences ADD PRIMARY KEY (user_id, key);

COMMIT;
