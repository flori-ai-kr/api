-- 알림 발송 멱등성 로그.
-- 같은 (user_id, notification_type, dedup_key) 알림을 중복 발송하지 않도록 원자적으로 기록한다.
-- 예: 일일 픽업 요약은 (user_id, 'daily_summary', '2026-05-25') 1건만 발송.
-- 스케줄러 재기동·중복 트리거 시 INSERT ... ON CONFLICT DO NOTHING 으로 1회 발송 보장(at-most-once).
CREATE TABLE notification_log (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  notification_type VARCHAR(40) NOT NULL,
  dedup_key VARCHAR(64) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (user_id, notification_type, dedup_key)
);
