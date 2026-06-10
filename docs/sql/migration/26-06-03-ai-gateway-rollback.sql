-- 26-06-03-ai-gateway.sql 롤백 — AI 게이트웨이 로깅 테이블 제거.
-- 트리거는 테이블 DROP 시 함께 제거된다. update_updated_at() 함수는 공용이라 보존.
START TRANSACTION;

DROP TABLE IF EXISTS ai_proactive_log;
DROP TABLE IF EXISTS ai_write_proposal;
DROP TABLE IF EXISTS ai_chat_message;
DROP TABLE IF EXISTS ai_chat_session;

COMMIT;
