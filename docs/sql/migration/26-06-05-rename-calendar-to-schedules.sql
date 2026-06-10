-- calendar_events → schedules 리네이밍 마이그레이션
-- 실행 전 백업 권장
-- bottom_nav_items의 "calendar" 값은 유지 (웹 NavItemKey와 동기화)

BEGIN;

-- 1. 테이블 리네이밍
ALTER TABLE calendar_events RENAME TO schedules;

-- 2. 인덱스 리네이밍
ALTER INDEX idx_calendar_events_user_id RENAME TO idx_schedules_user_id;
ALTER INDEX idx_calendar_events_range RENAME TO idx_schedules_range;

-- 3. 트리거 리네이밍
ALTER TRIGGER update_calendar_events_updated_at ON schedules RENAME TO update_schedules_updated_at;

COMMIT;

-- bottom_nav_items에서 "schedules" → "calendar" 되돌리기
UPDATE user_preferences
SET bottom_nav_items = REPLACE(bottom_nav_items::text, '"schedules"', '"calendar"')::jsonb
WHERE bottom_nav_items::text LIKE '%"schedules"%';

-- DEFAULT도 원복
ALTER TABLE user_preferences
    ALTER COLUMN bottom_nav_items SET DEFAULT '["calendar","sales","expenses","customers","insights"]'::jsonb;
