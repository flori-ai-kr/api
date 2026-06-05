-- =============================================
-- 5월 집중 목(mock) 데이터 — 2026년 5월
-- =============================================
-- seed-dev-mock.sql 적용 후 추가로 실행하면 5월 데이터가 더 풍성해짐.
-- 단독 실행도 가능(고객은 seed-dev-mock에서 만든 걸 재활용).
--
-- 사용법:
--   1) v_email 을 로그인하는 계정 이메일로 교체
--   2) psql "$DB_URL" -f docs/sql/seed-may-mock.sql
--
-- 주의: 멱등 아님 — 두 번 실행하면 중복된다.
-- =============================================
DO $$
DECLARE
  v_email   text := 'gkstkdgh2000@hanmail.net';  -- ← 로그인 계정 이메일로 교체
  v_uid     bigint;
  c_kim bigint; c_lee bigint; c_park bigint; c_choi bigint; c_jung bigint;
BEGIN
  SELECT id INTO v_uid FROM users WHERE email = v_email;
  IF v_uid IS NULL THEN RAISE EXCEPTION 'user 없음: %', v_email; END IF;

  -- 기존 고객 ID 조회
  SELECT id INTO c_kim FROM customers WHERE user_id = v_uid AND phone = '010-1111-2221';
  SELECT id INTO c_lee FROM customers WHERE user_id = v_uid AND phone = '010-1111-2222';
  SELECT id INTO c_park FROM customers WHERE user_id = v_uid AND phone = '010-1111-2223';
  SELECT id INTO c_choi FROM customers WHERE user_id = v_uid AND phone = '010-1111-2224';
  SELECT id INTO c_jung FROM customers WHERE user_id = v_uid AND phone = '010-1111-2225';

  -- ── 매출 (5월 추가분 — 어버이날 성수기 + 웨딩 시즌) ─────────────
  INSERT INTO sales(user_id,date,product_category,amount,payment_method,reservation_channel,customer_name,customer_phone,customer_id,memo,is_unpaid,has_review) VALUES
    -- 어버이날 주간 (5/5~5/10) 몰아치기
    (v_uid,'2026-05-05','basket',75000,'card','kakaotalk','정하늘','010-1111-2225',c_jung,'어머님 선물',false,true),
    (v_uid,'2026-05-05','basic_bouquet',42000,'cash','road',NULL,NULL,NULL,'워크인',false,false),
    (v_uid,'2026-05-06','medium_bouquet',58000,'card','naver_booking',NULL,NULL,NULL,'어버이날',false,false),
    (v_uid,'2026-05-06','potted_plant',35000,'naverpay','kakaotalk',NULL,NULL,NULL,NULL,false,false),
    (v_uid,'2026-05-07','basket',120000,'transfer','phone','이서준','010-1111-2222',c_lee,'시부모님',false,true),
    (v_uid,'2026-05-07','mini_bouquet',28000,'cash','road',NULL,NULL,NULL,NULL,false,false),
    (v_uid,'2026-05-07','mini_bouquet',28000,'cash','road',NULL,NULL,NULL,NULL,false,false),
    (v_uid,'2026-05-07','basic_bouquet',48000,'card','road',NULL,NULL,NULL,'카네이션+안개',false,false),
    (v_uid,'2026-05-08','basket',180000,'card','phone','김민지','010-1111-2221',c_kim,'매년 주문',false,true),
    (v_uid,'2026-05-08','medium_bouquet',55000,'naverpay','naver_booking',NULL,NULL,NULL,NULL,false,false),
    (v_uid,'2026-05-08','vase',65000,'card','kakaotalk','최유나','010-1111-2224',c_choi,NULL,false,false),
    (v_uid,'2026-05-09','mini_bouquet',20000,'cash','road',NULL,NULL,NULL,'재고 처리',false,false),
    -- 중순 (일상 매출)
    (v_uid,'2026-05-12','vase',60000,'transfer','kakaotalk','김민지','010-1111-2221',c_kim,'정기 교체',false,false),
    (v_uid,'2026-05-13','wreath',150000,'transfer','phone','박지후','010-1111-2223',c_park,'카페 오픈',false,false),
    (v_uid,'2026-05-15','mini_bouquet',25000,'cash','road',NULL,NULL,NULL,NULL,false,false),
    (v_uid,'2026-05-16','preserved',85000,'card','kakaotalk',NULL,NULL,NULL,'선물용',false,true),
    (v_uid,'2026-05-18','basic_bouquet',45000,'card','naver_booking',NULL,NULL,NULL,NULL,false,false),
    (v_uid,'2026-05-19','medium_bouquet',68000,'card','road','정하늘','010-1111-2225',c_jung,'작약 좋아하심',false,false),
    -- 하순 (웨딩시즌 시작)
    (v_uid,'2026-05-21','bridal_bouquet',200000,'transfer','phone','최유나','010-1111-2224',c_choi,'6/7 본식용 미리 상담',false,false),
    (v_uid,'2026-05-22','photo_bouquet',130000,'transfer','phone',NULL,NULL,NULL,'스냅 촬영 당일',false,false),
    (v_uid,'2026-05-24','basket',80000,'naverpay','naver_booking',NULL,NULL,NULL,NULL,false,false),
    (v_uid,'2026-05-25','basic_bouquet',45000,'card','road',NULL,NULL,NULL,NULL,false,false),
    (v_uid,'2026-05-27','large_bouquet',95000,'card','kakaotalk','이서준','010-1111-2222',c_lee,'기념일',false,true),
    (v_uid,'2026-05-28','mini_bouquet',25000,'cash','road',NULL,NULL,NULL,NULL,false,false),
    (v_uid,'2026-05-29','reservation',180000,'unpaid','phone','박지후','010-1111-2223',c_park,'개업2차 미수',true,false),
    (v_uid,'2026-05-30','medium_bouquet',55000,'naverpay','kakaotalk',NULL,NULL,NULL,NULL,false,false),
    (v_uid,'2026-05-31','special_bouquet',110000,'card','naver_booking','김민지','010-1111-2221',c_kim,'작약 시즌',false,true);

  -- ── 지출 (5월 추가분) ────────────────────────────────────
  INSERT INTO expenses(user_id,date,item_name,category,unit_price,quantity,total_amount,payment_method,vendor,memo) VALUES
    (v_uid,'2026-05-02','카네이션 추가 사입','flower_purchase',20000,15,300000,'transfer','양재 화훼공판장','어버이날 추가 물량'),
    (v_uid,'2026-05-04','리본/포장지 추가','supplies',80000,1,80000,'card','부자재상','어버이날 대비'),
    (v_uid,'2026-05-07','아르바이트 인건비','labor',120000,1,120000,'transfer',NULL,'어버이날 주간 도우미'),
    (v_uid,'2026-05-09','퀵 배송비','delivery',9000,6,54000,'cash',NULL,'어버이날 주간 배달'),
    (v_uid,'2026-05-10','카드 수수료 정산','etc',35000,1,35000,'card',NULL,'5월 초 카드 수수료'),
    (v_uid,'2026-05-14','작약 사입','flower_purchase',50000,4,200000,'transfer','양재 화훼공판장','작약 시즌'),
    (v_uid,'2026-05-16','인스타 광고','advertising',80000,1,80000,'card','Meta','5월 중순 부스트'),
    (v_uid,'2026-05-19','화훼공판장 정기 사입','flower_purchase',35000,8,280000,'transfer','양재 화훼공판장','장미/리시안/안개'),
    (v_uid,'2026-05-22','퀵 배송비','delivery',9000,3,27000,'cash',NULL,NULL),
    (v_uid,'2026-05-25','매장 청소 용역','maintenance',50000,1,50000,'transfer',NULL,'월 2회'),
    (v_uid,'2026-05-28','오아시스/글루건','supplies',25000,1,25000,'card','부자재상',NULL),
    (v_uid,'2026-05-30','웨딩 소재 사입','flower_purchase',60000,3,180000,'transfer','양재 화훼공판장','6월 웨딩 대비');

  -- ── 예약 (5월 중순~말) ──────────────────────────────────
  INSERT INTO reservations(user_id,date,time,customer_name,customer_phone,title,memo,status,amount,reminder_at) VALUES
    (v_uid,'2026-05-15','10:00','정하늘','010-1111-2225','작약 다발 픽업','핑크 작약 20송이','confirmed',68000,NULL),
    (v_uid,'2026-05-18','15:00','김민지','010-1111-2221','정기 화병 교체','거실 대형 화병','confirmed',60000,NULL),
    (v_uid,'2026-05-22','14:00','최유나','010-1111-2224','웨딩부케 상담','6/7 본식 + 촬영 2개','confirmed',330000,NULL),
    (v_uid,'2026-05-25','11:00','박지후','010-1111-2223','개업 화분 배달','다육+관엽 3종 세트','pending',120000,'2026-05-25 08:00:00'),
    (v_uid,'2026-05-28','16:00','이서준','010-1111-2222','기념일 꽃다발','서프라이즈 배달','confirmed',95000,'2026-05-28 10:00:00'),
    (v_uid,'2026-05-30','14:00','이서준','010-1111-2222','프로포즈 부케 픽업','레드 장미 100송이','confirmed',150000,'2026-05-30 09:00:00'),
    (v_uid,'2026-05-31','11:00','최유나','010-1111-2224','기념일 꽃다발','파스텔 톤','pending',60000,NULL);

  -- ── 일정 (5월) ──────────────────────────────────────
  INSERT INTO schedules(user_id,title,start_date,end_date,color,memo) VALUES
    (v_uid,'어버이날 성수기','2026-05-05','2026-05-09','#f43f5e','카네이션 대량 확보, 아르바이트 투입'),
    (v_uid,'화훼공판장 정기 사입','2026-05-05','2026-05-05','#3b82f6',NULL),
    (v_uid,'작약 시즌 시작','2026-05-12','2026-05-31','#ec4899','작약 입고 체크'),
    (v_uid,'화훼공판장 정기 사입','2026-05-19','2026-05-19','#3b82f6',NULL),
    (v_uid,'웨딩시즌 준비 시작','2026-05-20','2026-05-31','#8b5cf6','부케 소재 사전 확보'),
    (v_uid,'매장 정기 휴무','2026-05-12','2026-05-12','#6b7280','월요일 휴무'),
    (v_uid,'매장 정기 휴무','2026-05-19','2026-05-19','#6b7280','월요일 휴무'),
    (v_uid,'매장 정기 휴무','2026-05-26','2026-05-26','#6b7280','월요일 휴무');

  RAISE NOTICE '5월 시드 완료 user_id=%', v_uid;
END $$;
