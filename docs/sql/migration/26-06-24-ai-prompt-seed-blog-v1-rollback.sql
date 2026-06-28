-- 26-06-24-ai-prompt-seed-blog-v1.sql 롤백 — 시드한 blog v1 행 제거.
-- 주의: 시드 후 콘솔에서 v1을 편집했더라도 함께 삭제된다(시드 자체를 되돌리는 용도).
START TRANSACTION;

DELETE FROM ai_prompt WHERE channel = 'blog' AND version = 'v1';

COMMIT;
