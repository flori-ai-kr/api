-- notification_send_logs.source CHECK 확장: 'alimtalk' 채널 허용.
-- 사업자 인증 알림톡(접수·승인·거절) 발송 결과를 발송 로그에 기록한다.
-- source=채널('alimtalk'), type=도메인('business_verification', CHECK 없는 자유값).
-- 'alimtalk'은 8자라 컬럼 길이는 기존 VARCHAR(20) 그대로 충분 — 폭 변경 없음.
START TRANSACTION;

ALTER TABLE notification_send_logs
  DROP CONSTRAINT IF EXISTS notification_send_logs_source_check;

ALTER TABLE notification_send_logs
  ADD CONSTRAINT notification_send_logs_source_check
    CHECK (source IN ('web', 'cron', 'system', 'alimtalk'));

COMMIT;
