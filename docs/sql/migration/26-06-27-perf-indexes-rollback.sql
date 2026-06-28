-- 26-06-27-perf-indexes.sql 롤백 — 추가한 성능 복합 인덱스 제거.
-- ⚠️ DROP INDEX CONCURRENTLY 도 트랜잭션 블록 안에서 실행 불가 → autocommit, 문장별 실행.
DROP INDEX CONCURRENTLY IF EXISTS idx_sales_user_date;
DROP INDEX CONCURRENTLY IF EXISTS idx_expenses_user_date;
DROP INDEX CONCURRENTLY IF EXISTS idx_reservations_user_date;
DROP INDEX CONCURRENTLY IF EXISTS idx_sales_user_customer;
DROP INDEX CONCURRENTLY IF EXISTS idx_sales_user_phone_date;
DROP INDEX CONCURRENTLY IF EXISTS idx_reservations_due;
DROP INDEX CONCURRENTLY IF EXISTS idx_photo_cards_user_updated;
