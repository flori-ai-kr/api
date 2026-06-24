-- 백필 실행 전 점검(읽기 전용). 26-06-24-photo-cards-backfill-customer-from-sale.sql 가
-- 무엇을 바꿀지 미리 확인한다. 모두 SELECT 라 데이터를 변경하지 않는다.

-- ── 1) 전체 영향 건수: 이번 백필로 새로 고객에 연결될 카드 수 ──────────────
SELECT count(*) AS total_cards_to_backfill
FROM photo_cards pc
JOIN sales s
  ON pc.sale_id = s.id
 AND s.user_id = pc.user_id
WHERE pc.customer_id IS NULL
  AND s.customer_id IS NOT NULL;

-- ── 2) 유저별 영향 건수 ────────────────────────────────────────────────
SELECT pc.user_id,
       count(*) AS cards_to_backfill
FROM photo_cards pc
JOIN sales s
  ON pc.sale_id = s.id
 AND s.user_id = pc.user_id
WHERE pc.customer_id IS NULL
  AND s.customer_id IS NOT NULL
GROUP BY pc.user_id
ORDER BY cards_to_backfill DESC;

-- ── 3) 상세 미리보기: 어떤 카드가 어떤 고객으로 연결될지 (최근 100건) ──────
SELECT pc.id              AS photo_card_id,
       pc.user_id,
       pc.title,
       pc.sale_id,
       s.customer_id      AS will_link_customer_id,
       c.name             AS will_link_customer_name,
       pc.created_at
FROM photo_cards pc
JOIN sales s
  ON pc.sale_id = s.id
 AND s.user_id = pc.user_id
LEFT JOIN customers c
  ON c.id = s.customer_id
 AND c.user_id = pc.user_id
WHERE pc.customer_id IS NULL
  AND s.customer_id IS NOT NULL
ORDER BY pc.created_at DESC
LIMIT 100;

-- ── 4) 전체 연결 상태 분포: 백필 후에도 남는 미연결분을 이해하기 위한 진단 ──
--   already_linked        : 이미 고객 연결됨(변화 없음)
--   will_backfill         : 이번 백필로 연결될 카드(= 1번 수치)
--   sale_has_no_customer  : 매출은 연결됐으나 그 매출에 고객이 없어 여전히 NULL로 남음
--   no_sale_no_customer   : 매출도 고객도 없는 순수 갤러리 카드(연결 대상 아님)
--   sale_other_tenant     : sale_id가 가리키는 매출이 같은 유저 소유가 아님(데이터 무결성 이상 — 0이어야 정상)
SELECT
  count(*) FILTER (WHERE pc.customer_id IS NOT NULL)
    AS already_linked,
  count(*) FILTER (WHERE pc.customer_id IS NULL AND s.customer_id IS NOT NULL)
    AS will_backfill,
  count(*) FILTER (WHERE pc.customer_id IS NULL AND pc.sale_id IS NOT NULL AND s.id IS NOT NULL AND s.customer_id IS NULL)
    AS sale_has_no_customer,
  count(*) FILTER (WHERE pc.customer_id IS NULL AND pc.sale_id IS NULL)
    AS no_sale_no_customer,
  count(*) FILTER (WHERE pc.customer_id IS NULL AND pc.sale_id IS NOT NULL AND s.id IS NULL)
    AS sale_other_tenant
FROM photo_cards pc
LEFT JOIN sales s
  ON pc.sale_id = s.id
 AND s.user_id = pc.user_id;
