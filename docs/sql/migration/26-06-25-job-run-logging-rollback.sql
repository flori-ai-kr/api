-- 롤백: 백그라운드 작업 실행 로깅 테이블 제거.
START TRANSACTION;

DROP TABLE IF EXISTS job_run_logs;
DROP TABLE IF EXISTS job_run_status;

COMMIT;
