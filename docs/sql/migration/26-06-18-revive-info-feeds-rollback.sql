-- 26-06-18-revive-info-feeds.sql 롤백 — 정보 피드 4테이블 제거.
-- 데이터는 복원되지 않는다(드롭 시 소실).

START TRANSACTION;

DROP TABLE IF EXISTS insight_scraps;
DROP TABLE IF EXISTS support_programs;
DROP TABLE IF EXISTS flower_auction_prices;
DROP TABLE IF EXISTS trend_articles;

COMMIT;
