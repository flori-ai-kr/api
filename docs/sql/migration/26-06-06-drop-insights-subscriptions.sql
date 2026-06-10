-- 인사이트(트렌드/인스타그램/스크랩) + 구독(IAP/RevenueCat) 도메인 제거.
-- 관련 백엔드 API·로직·테스트도 함께 삭제됨(웹 전용 운영 결정). 실행 전 백업 권장.

BEGIN;

-- 인사이트
DROP TABLE IF EXISTS insight_scraps;
DROP TABLE IF EXISTS instagram_posts;
DROP TABLE IF EXISTS instagram_accounts;
DROP TABLE IF EXISTS trend_articles;

-- 구독/결제
DROP TABLE IF EXISTS subscription_events;
DROP TABLE IF EXISTS subscriptions;

-- 하단바 기본값에서 'insights' 제거(기본값 + 기존 사용자 행)
ALTER TABLE user_preferences
  ALTER COLUMN bottom_nav_items SET DEFAULT '["calendar","sales","expenses","customers"]'::jsonb;

UPDATE user_preferences
SET bottom_nav_items = (
  SELECT COALESCE(jsonb_agg(elem), '[]'::jsonb)
  FROM jsonb_array_elements(bottom_nav_items) AS elem
  WHERE elem <> '"insights"'::jsonb
)
WHERE bottom_nav_items::text LIKE '%insights%';

COMMIT;
