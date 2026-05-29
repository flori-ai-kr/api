-- 26-05-29-refresh-token-tracking 롤백. status → revoked(boolean)로 되돌리고 추가 컬럼/인덱스/트리거 제거.
START TRANSACTION;

DROP TRIGGER IF EXISTS update_refresh_tokens_updated_at ON refresh_tokens;
DROP INDEX IF EXISTS idx_refresh_tokens_client;
DROP INDEX IF EXISTS idx_refresh_tokens_user_last_used;

-- status → revoked 복원: ACTIVE가 아니면 revoked=true.
ALTER TABLE refresh_tokens ADD COLUMN revoked BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE refresh_tokens SET revoked = TRUE WHERE status <> 'ACTIVE';

ALTER TABLE refresh_tokens
  DROP COLUMN updated_at,
  DROP COLUMN last_used_at,
  DROP COLUMN session_started_at,
  DROP COLUMN reissued_count,
  DROP COLUMN status,
  DROP COLUMN created_ip,
  DROP COLUMN user_agent,
  DROP COLUMN device_id,
  DROP COLUMN client_id,
  DROP COLUMN parent_token_id;

COMMIT;
