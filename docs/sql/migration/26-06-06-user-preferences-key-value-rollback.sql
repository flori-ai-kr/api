-- 26-06-06-user-preferences-key-value.sql 롤백 — 단일 컬럼(bottom_nav_items) 구조로 복원.
-- key='bottom_nav' 외 다른 key 행은 폐기된다(원래 구조엔 없던 데이터).

BEGIN;

ALTER TABLE user_preferences ADD COLUMN bottom_nav_items JSONB;
UPDATE user_preferences SET bottom_nav_items = value WHERE key = 'bottom_nav';
DELETE FROM user_preferences WHERE key <> 'bottom_nav';

ALTER TABLE user_preferences DROP CONSTRAINT user_preferences_pkey;
ALTER TABLE user_preferences DROP COLUMN key;
ALTER TABLE user_preferences DROP COLUMN value;
ALTER TABLE user_preferences DROP COLUMN created_at;
ALTER TABLE user_preferences ALTER COLUMN bottom_nav_items SET NOT NULL;
ALTER TABLE user_preferences
  ALTER COLUMN bottom_nav_items SET DEFAULT '["calendar","sales","expenses","customers"]'::jsonb;
ALTER TABLE user_preferences ADD PRIMARY KEY (user_id);

COMMIT;
