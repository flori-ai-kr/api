-- 26-06-24-notification-source-alimtalk.sql 롤백.
-- source CHECK를 원래 값('web','cron','system')으로 되돌린다.
-- 주의: 되돌리기 전 source='alimtalk' 행이 있으면 제약 추가가 실패하므로 사전 정리 필요.
START TRANSACTION;

ALTER TABLE notification_send_logs
  DROP CONSTRAINT IF EXISTS notification_send_logs_source_check;

ALTER TABLE notification_send_logs
  ADD CONSTRAINT notification_send_logs_source_check
    CHECK (source IN ('web', 'cron', 'system'));

COMMIT;
