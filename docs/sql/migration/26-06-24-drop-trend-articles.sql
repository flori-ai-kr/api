-- 트렌드(트렌드/뉴스) 인사이트 기능 제거 — trend_articles 드롭 + insight_scraps CHECK 축소.
-- KEEP: 경매 시세(flower_auction_prices)·지원사업(support_programs)·스크랩(insight_scraps, flower_item_scraps).
-- 트렌드 적재(네이버 뉴스검색)와 관련 API/스크랩 대상('trend')을 전부 제거한다.
--
-- 순서:
--   1) insight_scraps 에서 target_type='trend' 행 삭제(잔여 트렌드 스크랩 정리).
--   2) insight_scraps.target_type CHECK 제약을 ('trend','grant') → ('grant') 으로 교체.
--   3) trend_articles 테이블 드롭(인덱스도 함께 사라짐).
-- 데이터는 복원되지 않는다(드롭 시 소실). 롤백은 26-06-24-drop-trend-articles-rollback.sql.

START TRANSACTION;

-- 1) 트렌드 대상 스크랩 정리(새 CHECK 제약 위반 방지).
DELETE FROM insight_scraps WHERE target_type = 'trend';

-- 2) target_type CHECK 교체: 'trend' 제거, 'grant' 만 허용.
--    인라인 CHECK 의 자동 생성 제약명(insight_scraps_target_type_check)을 드롭하고 새 제약을 추가한다.
--    제약명이 다른 환경(과거 수동 적용 등)을 대비해 카탈로그에서 실제 이름을 찾아 드롭한다.
DO $$
DECLARE
  con_name TEXT;
BEGIN
  SELECT conname INTO con_name
  FROM pg_constraint
  WHERE conrelid = 'insight_scraps'::regclass
    AND contype = 'c'
    AND pg_get_constraintdef(oid) ILIKE '%target_type%';
  IF con_name IS NOT NULL THEN
    EXECUTE format('ALTER TABLE insight_scraps DROP CONSTRAINT %I', con_name);
  END IF;
END $$;

ALTER TABLE insight_scraps
  ADD CONSTRAINT insight_scraps_target_type_check CHECK (target_type IN ('grant'));

-- 3) 트렌드·뉴스 기사 테이블 드롭.
DROP TABLE IF EXISTS trend_articles;

COMMIT;
