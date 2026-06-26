-- notification_send_logs.source CHECK 확장: 'web_push'·'fcm' 채널 허용.
-- PushDispatcher가 모든 푸시 발송을 발송 1건마다 발송 로그에 기록하면서 채널을 source로 남긴다.
-- source=채널('web_push'=브라우저 Web Push, 'fcm'=모바일 FCM), type=푸시타입(CHECK 없는 자유값).
-- 'web_push'는 8자라 컬럼 길이 VARCHAR(20) 그대로 충분 — 폭 변경 없음.
-- 제약명 근거: 원본 DDL의 source 인라인 단일 CHECK는 PostgreSQL이 자동으로
-- 'notification_send_logs_source_check'로 명명한다 → 같은 이름으로 DROP/재추가하면 멱등.
-- ⚠️ 이 마이그레이션 미적용 시 web_push/fcm insert가 CHECK 위반으로 실패 → 발송 로깅이 조용히 0건이 된다.
START TRANSACTION;

ALTER TABLE notification_send_logs
  DROP CONSTRAINT IF EXISTS notification_send_logs_source_check;

ALTER TABLE notification_send_logs
  ADD CONSTRAINT notification_send_logs_source_check
    CHECK (source IN ('web', 'cron', 'system', 'alimtalk', 'web_push', 'fcm'));

COMMIT;
