-- calendar_events → schedules 리네이밍 마이그레이션
-- 실행 전 백업 권장

BEGIN;

-- 1. 테이블 리네이밍
ALTER TABLE calendar_events RENAME TO schedules;

-- 2. 인덱스 리네이밍
ALTER INDEX idx_calendar_events_user_id RENAME TO idx_schedules_user_id;
ALTER INDEX idx_calendar_events_range RENAME TO idx_schedules_range;

-- 3. 트리거 리네이밍
ALTER TRIGGER update_calendar_events_updated_at ON schedules RENAME TO update_schedules_updated_at;

-- 4. bottom_nav_items 기본값 변경
ALTER TABLE user_preferences
  ALTER COLUMN bottom_nav_items SET DEFAULT '["schedules","sales","expenses","customers","insights"]'::jsonb;

-- 5. 기존 레코드의 bottom_nav_items에서 "calendar" → "schedules" 치환
UPDATE user_preferences
SET bottom_nav_items = REPLACE(bottom_nav_items::text, '"calendar"', '"schedules"')::jsonb
WHERE bottom_nav_items::text LIKE '%"calendar"%';

COMMIT;
