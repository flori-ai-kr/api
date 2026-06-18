-- 26-06-19-ai-marketing.sql 롤백 — AI 마케팅 게이트웨이 테이블 제거.
-- 트리거는 테이블 DROP 시 함께 제거된다. update_updated_at() 함수는 공용이라 보존.
START TRANSACTION;

DROP TABLE IF EXISTS ai_marketing_content;
DROP TABLE IF EXISTS ai_tone_profile;

COMMIT;
