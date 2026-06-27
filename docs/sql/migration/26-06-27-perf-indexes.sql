-- 성능 복합 인덱스 (2026-06-27, session2-perf)
-- 대시보드/통계/목록 핫 경로(user_id 등치 + date 범위·정렬), 재방문 상관 서브쿼리(customer_phone),
-- 리마인더 due 스캔(reminder_at), 갤러리 키셋 페이지네이션(updated_at,id)을 복합 인덱스로 보강.
-- 단일컬럼 인덱스(idx_*_user_id, idx_*_date)만 있던 테이블을 보완한다(전부 신규 — 기존과 중복 없음).
--
-- ⚠️ 적용 방법 (프로덕션 RDS = 라이브):
--   CREATE INDEX CONCURRENTLY 는 트랜잭션 블록 안에서 실행할 수 없다. 반드시 autocommit 으로,
--   문장을 하나씩 실행할 것. START TRANSACTION/BEGIN 으로 감싸지 말 것.
--     psql:    psql "$DB_URL" -f 26-06-27-perf-indexes.sql        (psql 은 문장별 실행 → OK)
--     DataGrip: 각 문장을 개별 실행하거나 Tx 모드를 Auto-commit 으로 두고 실행.
--   CONCURRENTLY 는 쓰기 잠금 없이 생성하므로 서비스 중단이 없다(대신 생성 시간이 더 걸릴 수 있음).
--   중간 실패로 INVALID 인덱스가 남으면 해당 인덱스를 DROP INDEX 후 재실행한다.
--   IF NOT EXISTS 라 재실행해도 안전하다.
-- 롤백: 26-06-27-perf-indexes-rollback.sql

-- 매출: user_id + date 범위/정렬 (통계 6쿼리/요청·대시보드·목록)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sales_user_date ON sales(user_id, date);

-- 지출: user_id + date 범위/정렬 (통계·대시보드·목록)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_expenses_user_date ON expenses(user_id, date);

-- 예약: user_id + date 범위/정렬 (캘린더·통계)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_reservations_user_date ON reservations(user_id, date);

-- 고객 통계 집계: WHERE user_id=? AND customer_id IS NOT NULL GROUP BY customer_id (매 GET /customers)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sales_user_customer ON sales(user_id, customer_id) WHERE customer_id IS NOT NULL;

-- 대시보드 재방문 고객 상관 서브쿼리: EXISTS(... p.user_id=? AND p.customer_phone=? AND p.date<?)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sales_user_phone_date ON sales(user_id, customer_phone, date) WHERE customer_phone IS NOT NULL;

-- 리마인더 due 스캔(5분마다 전테넌트): WHERE reminder_sent=false AND reminder_at<=now (부분 인덱스)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_reservations_due ON reservations(reminder_at) WHERE reminder_sent = FALSE;

-- 갤러리 키셋 페이지네이션: ORDER BY updated_at DESC, id DESC (테넌트별)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_photo_cards_user_updated ON photo_cards(user_id, updated_at DESC, id DESC);
