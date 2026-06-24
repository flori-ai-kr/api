-- photo_cards.customer_id 백필: sale 경유로 고객을 상속 (catch-up).
--
-- 배경: 26-06-08 컬럼 추가 시 1회 백필했으나, 그 이후 매출/캘린더 플로우로 생성된
--   사진 카드는 customer_id를 채우지 않아(코드가 sale의 고객을 상속하지 않음) 다시
--   NULL로 쌓였다. 사진첩 고객 필터·고객 상세 집계가 photo_cards.customer_id 만 보므로
--   이 카드들은 고객에 연결되지 않는다. 코드는 PhotoCardService가 saleId로부터 고객을
--   상속하도록 수정됐고(신규분 해결), 이 스크립트는 누적된 기존분을 일괄 연결한다.
--
-- 멱등: customer_id IS NULL 인 행만 채우므로 여러 번 실행해도 안전하다.
-- 비가역: 백필된 값과 원래 설정된 값을 구분할 수 없어 별도 롤백 스크립트를 두지 않는다
--   (필요 시 실행 전 영향 범위를 -check.sql 로 확인할 것).
-- 실행 순서: 코드(api 상속 수정)를 먼저 배포한 뒤 실행할 것. 코드 배포 전에 돌리면
--   백필 이후에도 매출/캘린더 플로우가 NULL 행을 계속 쌓아 다시 어긋난다.
BEGIN;

UPDATE photo_cards pc
SET customer_id = s.customer_id
FROM sales s
WHERE pc.sale_id = s.id
  AND pc.customer_id IS NULL
  AND s.customer_id IS NOT NULL
  AND s.user_id = pc.user_id;  -- 테넌트 격리(같은 유저의 sale 만 상속)

COMMIT;
