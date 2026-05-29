-- refresh_tokens 세션 추적/통계 컬럼 추가 + revoked → status(enum) 전환.
-- 목적: 발급 컨텍스트(채널/기기/UA/IP) + 회전 계보(parent/세션시작/재발급수) + 종료 사유(status)를
--       축적해 추후 세션/로그인 통계(채널별 사용·기기 분포·세션 lifetime·DAU 등)에 활용.
START TRANSACTION;

ALTER TABLE refresh_tokens
  ADD COLUMN parent_token_id    BIGINT,
  ADD COLUMN client_id          VARCHAR(64),
  ADD COLUMN device_id          VARCHAR(128),
  ADD COLUMN user_agent         VARCHAR(512),
  ADD COLUMN created_ip         VARCHAR(45),
  ADD COLUMN status             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
    CHECK (status IN ('ACTIVE', 'ROTATED', 'LOGGED_OUT', 'EXPIRED')),
  ADD COLUMN reissued_count     INT NOT NULL DEFAULT 0,
  ADD COLUMN session_started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  ADD COLUMN last_used_at       TIMESTAMPTZ,
  ADD COLUMN updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- 기존 revoked → status 변환: 종료 사유를 구분할 수 없는 기존 데이터는 일괄 ROTATED로 본다.
UPDATE refresh_tokens SET status = 'ROTATED' WHERE revoked = TRUE;
ALTER TABLE refresh_tokens DROP COLUMN revoked;

CREATE INDEX idx_refresh_tokens_user_last_used ON refresh_tokens(user_id, last_used_at);
CREATE INDEX idx_refresh_tokens_client ON refresh_tokens(client_id);

CREATE TRIGGER update_refresh_tokens_updated_at
  BEFORE UPDATE ON refresh_tokens FOR EACH ROW EXECUTE FUNCTION update_updated_at();

COMMIT;
