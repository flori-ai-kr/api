-- notification_send_logs.source CHECK 확장: 'alimtalk' 채널 허용.
-- 사업자 인증 알림톡(접수·승인·거절) 발송 결과를 발송 로그에 기록한다.
-- source=채널('alimtalk'), type=도메인('business_verification', CHECK 없는 자유값).
-- 'alimtalk'은 8자라 컬럼 길이는 기존 VARCHAR(20) 그대로 충분 — 폭 변경 없음.
-- 제약명 근거: 원본 DDL의 source 컬럼 인라인 단일 CHECK는 PostgreSQL이 자동으로
-- '{table}_{column}_check' = 'notification_send_logs_source_check'로 명명한다(Zonky PG로 실측 확인).
-- 따라서 아래 DROP이 기존 제약을 정확히 제거하고 같은 이름으로 재추가 → 멱등하며 중복 CHECK가 남지 않는다.
START TRANSACTION;

ALTER TABLE notification_send_logs
  DROP CONSTRAINT IF EXISTS notification_send_logs_source_check;

ALTER TABLE notification_send_logs
  ADD CONSTRAINT notification_send_logs_source_check
    CHECK (source IN ('web', 'cron', 'system', 'alimtalk'));

COMMIT;
