-- 롤백: 푸시 알림 타입별 수신 설정 제거.
START TRANSACTION;

DROP TABLE IF EXISTS notification_preferences;

COMMIT;
