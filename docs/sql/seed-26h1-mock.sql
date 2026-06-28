-- =============================================
-- 개발용 목(mock) 데이터 — 2026년 1~6월 (현행 스키마)
-- =============================================
-- 로컬/개발 화면 확인용. 운영 적용 금지.
-- 전제: all-tables-ddl.sql + migration 전체 적용, user 2(bbang.kakao)의
--       label_settings / customer_grades 기본 시드가 존재할 것.
--
-- 동작:
--   1) 비즈니스 테이블 17개 전체 행 삭제 (users/label_settings/customer_grades 등 보존)
--   2) 2026-01-01 ~ 2026-06-10 매출(~242건) + 6월 중하순 미래 예약
--      고객 25명(등급=실제 구매횟수 정합), 지출(고정비 템플릿 4종 연결),
--      일정, 커뮤니티(3계정 교차) 시드
--
-- 사용법: psql "$DB_URL" -f docs/sql/seed-26h1-mock.sql
-- 주의: 멱등 아님(앞의 삭제가 리셋 역할). 전체가 단일 트랜잭션.
-- =============================================

-- 직전 실행이 중단되어 트랜잭션이 abort 상태로 남아있으면 정리한다.
-- (진행 중 트랜잭션이 없으면 "no transaction in progress" 경고만 뜨고 무해하게 넘어감)
ROLLBACK;

BEGIN;

-- ── 헬퍼: 라벨 lookup 매출 insert ─────────────────────────
CREATE OR REPLACE FUNCTION pg_temp.fsale(
  p_uid bigint, p_date date, p_cat text, p_amt int,
  p_pay text, p_ch text, p_cust bigint, p_memo text,
  p_unpaid boolean DEFAULT false, p_review boolean DEFAULT false
) RETURNS bigint AS $fn$
DECLARE
  v_id bigint; v_cat bigint; v_pm bigint; v_ch bigint;
  v_name varchar; v_phone varchar; v_ts timestamptz;
BEGIN
  SELECT id INTO STRICT v_cat FROM label_settings
    WHERE user_id = p_uid AND domain = 'sale' AND kind = 'category' AND value = p_cat;
  IF p_pay IS NOT NULL THEN
    SELECT id INTO STRICT v_pm FROM label_settings
      WHERE user_id = p_uid AND domain = 'sale' AND kind = 'payment' AND value = p_pay;
  END IF;
  SELECT id INTO STRICT v_ch FROM label_settings
    WHERE user_id = p_uid AND domain = 'sale' AND kind = 'channel' AND value = p_ch;
  IF p_cust IS NOT NULL THEN
    SELECT name, phone INTO v_name, v_phone FROM customers WHERE id = p_cust;
  END IF;
  v_ts := p_date::timestamptz + interval '9 hours' + (floor(random() * 8)::int * interval '1 hour');
  INSERT INTO sales(user_id, date, category_id, amount, payment_method_id, channel_id,
                    customer_name, customer_phone, customer_id, memo, is_unpaid, has_review,
                    created_at, updated_at)
  VALUES (p_uid, p_date, v_cat, p_amt, v_pm, v_ch, v_name, v_phone, p_cust, p_memo,
          p_unpaid, p_review, v_ts, v_ts)
  RETURNING id INTO v_id;
  RETURN v_id;
END $fn$ LANGUAGE plpgsql;

-- ── 헬퍼: 예약→매출 전환 완료 건 (sale + completed reservation 페어) ──
CREATE OR REPLACE FUNCTION pg_temp.fres_done(
  p_uid bigint, p_date date, p_time time, p_cat text, p_amt int,
  p_pay text, p_ch text, p_cust bigint, p_title text, p_memo text,
  p_review boolean DEFAULT false
) RETURNS void AS $fn$
DECLARE v_sale bigint; v_name varchar; v_phone varchar; v_ts timestamptz;
BEGIN
  v_sale := pg_temp.fsale(p_uid, p_date, p_cat, p_amt, p_pay, p_ch, p_cust, p_memo, false, p_review);
  SELECT name, phone INTO v_name, v_phone FROM customers WHERE id = p_cust;
  v_ts := (p_date - 3)::timestamptz + interval '11 hours';
  INSERT INTO reservations(user_id, date, time, customer_name, customer_phone, title, memo,
                           status, sale_id, amount, pickup_completed, created_at, updated_at)
  VALUES (p_uid, p_date, p_time, COALESCE(v_name, ''), v_phone, p_title, p_memo,
          'completed', v_sale, p_amt, true, v_ts, p_date::timestamptz + interval '18 hours');
END $fn$ LANGUAGE plpgsql;

-- ── 헬퍼: 미전환 예약 (미래/취소) ─────────────────────────
CREATE OR REPLACE FUNCTION pg_temp.fres(
  p_uid bigint, p_date date, p_time time, p_cust bigint, p_title text, p_memo text,
  p_status text, p_amt int, p_reminder timestamptz DEFAULT NULL
) RETURNS void AS $fn$
DECLARE v_name varchar; v_phone varchar; v_ts timestamptz;
BEGIN
  SELECT name, phone INTO v_name, v_phone FROM customers WHERE id = p_cust;
  v_ts := LEAST(p_date - 3, DATE '2026-06-09')::timestamptz + interval '11 hours';
  INSERT INTO reservations(user_id, date, time, customer_name, customer_phone, title, memo,
                           status, amount, reminder_at, created_at, updated_at)
  VALUES (p_uid, p_date, p_time, COALESCE(v_name, ''), v_phone, p_title, p_memo,
          p_status, p_amt, p_reminder, v_ts, v_ts);
END $fn$ LANGUAGE plpgsql;

-- ── 헬퍼: 지출 insert (total = unit*qty 서버 SSOT 규칙) ────
CREATE OR REPLACE FUNCTION pg_temp.fexp(
  p_uid bigint, p_date date, p_item text, p_cat text, p_unit int, p_qty int,
  p_pay text, p_vendor text, p_memo text, p_recurring bigint DEFAULT NULL
) RETURNS void AS $fn$
DECLARE v_cat bigint; v_pm bigint; v_ts timestamptz;
BEGIN
  SELECT id INTO STRICT v_cat FROM label_settings
    WHERE user_id = p_uid AND domain = 'expense' AND kind = 'category' AND value = p_cat;
  SELECT id INTO STRICT v_pm FROM label_settings
    WHERE user_id = p_uid AND domain = 'expense' AND kind = 'payment' AND value = p_pay;
  v_ts := p_date::timestamptz + interval '19 hours';
  INSERT INTO expenses(user_id, date, item_name, category_id, unit_price, quantity, total_amount,
                       payment_method_id, vendor, memo, recurring_id, created_at, updated_at)
  VALUES (p_uid, p_date, p_item, v_cat, p_unit, p_qty, p_unit * p_qty,
          v_pm, p_vendor, p_memo, p_recurring, v_ts, v_ts);
END $fn$ LANGUAGE plpgsql;

-- ── 헬퍼: 익명 워크인 매출 생성기 ─────────────────────────
CREATE OR REPLACE FUNCTION pg_temp.fwalkins(
  p_uid bigint, p_from date, p_to date, p_n int, p_mode text DEFAULT 'normal'
) RETURNS void AS $fn$
DECLARE
  i int; d date; r numeric; cat text; amt int; pay text; ch text;
BEGIN
  FOR i IN 1..p_n LOOP
    d := p_from + floor(random() * (p_to - p_from + 1))::int;
    r := random();
    IF p_mode = 'graduation' THEN
      cat := CASE WHEN r < 0.45 THEN 'mini_bouquet' WHEN r < 0.80 THEN 'basic_bouquet'
                  WHEN r < 0.92 THEN 'medium_bouquet' ELSE 'basket' END;
    ELSIF p_mode = 'parents' THEN
      cat := CASE WHEN r < 0.45 THEN 'basket' WHEN r < 0.70 THEN 'basic_bouquet'
                  WHEN r < 0.85 THEN 'mini_bouquet' WHEN r < 0.95 THEN 'medium_bouquet'
                  ELSE 'potted_plant' END;
    ELSE
      cat := CASE WHEN r < 0.22 THEN 'mini_bouquet' WHEN r < 0.52 THEN 'basic_bouquet'
                  WHEN r < 0.65 THEN 'medium_bouquet' WHEN r < 0.75 THEN 'basket'
                  WHEN r < 0.83 THEN 'vase' WHEN r < 0.90 THEN 'potted_plant'
                  WHEN r < 0.95 THEN 'preserved' ELSE 'large_bouquet' END;
    END IF;
    amt := CASE cat
      WHEN 'mini_bouquet'   THEN 20000 + floor(random() * 13)::int * 1000
      WHEN 'basic_bouquet'  THEN 38000 + floor(random() * 18)::int * 1000
      WHEN 'medium_bouquet' THEN 55000 + floor(random() * 16)::int * 1000
      WHEN 'basket'         THEN 70000 + floor(random() * 51)::int * 1000
      WHEN 'vase'           THEN 55000 + floor(random() * 21)::int * 1000
      WHEN 'potted_plant'   THEN 30000 + floor(random() * 31)::int * 1000
      WHEN 'preserved'      THEN 70000 + floor(random() * 31)::int * 1000
      ELSE                       85000 + floor(random() * 26)::int * 1000 END;
    r := random();
    pay := CASE WHEN r < 0.55 THEN 'card' WHEN r < 0.75 THEN 'cash'
                WHEN r < 0.90 THEN 'naverpay' ELSE 'transfer' END;
    r := random();
    ch := CASE WHEN r < 0.45 THEN 'road' WHEN r < 0.70 THEN 'kakaotalk'
               WHEN r < 0.90 THEN 'naver_booking' ELSE 'phone' END;
    PERFORM pg_temp.fsale(p_uid, d, cat, amt, pay, ch, NULL, NULL, false, random() < 0.12);
  END LOOP;
END $fn$ LANGUAGE plpgsql;

-- =============================================
-- 메인 시드
-- =============================================
DO $seed$
DECLARE
  v_uid bigint;  -- 메인: bbang.kakao
  v_u1  bigint;  -- 보조: bbang.google
  v_u8  bigint;  -- 보조: bbang.naver
  g_new bigint; g_reg bigint; g_vip bigint; g_blk bigint;
  c01 bigint; c02 bigint; c03 bigint; c04 bigint; c05 bigint;
  c06 bigint; c07 bigint; c08 bigint; c09 bigint; c10 bigint;
  c11 bigint; c12 bigint; c13 bigint; c14 bigint; c15 bigint;
  c16 bigint; c17 bigint; c18 bigint; c19 bigint; c20 bigint;
  c21 bigint; c22 bigint; c23 bigint; c24 bigint; c25 bigint;
  r_rent bigint; r_net bigint; r_water bigint; r_ins bigint;
  p1 bigint; p2 bigint; p3 bigint; p4 bigint; p5 bigint; p6 bigint;
  p7 bigint; p8 bigint; p9 bigint; p10 bigint; p11 bigint; p12 bigint;
  cm bigint; v_m int;
BEGIN
  SELECT id INTO STRICT v_uid FROM users WHERE email = 'gkstkdgh2000@hanmail.net';
  SELECT id INTO STRICT v_u1  FROM users WHERE email = 'hchsa77@gmail.com';
  SELECT id INTO STRICT v_u8  FROM users WHERE email = 'hsh111366@naver.com';
  SELECT id INTO STRICT g_new FROM customer_grades WHERE user_id = v_uid AND name = '신규';
  SELECT id INTO STRICT g_reg FROM customer_grades WHERE user_id = v_uid AND name = '단골';
  SELECT id INTO STRICT g_vip FROM customer_grades WHERE user_id = v_uid AND name = 'VIP';
  SELECT id INTO STRICT g_blk FROM customer_grades WHERE user_id = v_uid AND name = '블랙리스트';

  -- ── 1) 비즈니스 테이블 초기화 (계정·라벨·인증 보존) ──────
  DELETE FROM sales;
  DELETE FROM expenses;
  DELETE FROM recurring_skips;
  DELETE FROM recurring_expenses;
  DELETE FROM reservations;
  DELETE FROM schedules;
  DELETE FROM customers;
  DELETE FROM community_likes;
  DELETE FROM community_comments;
  DELETE FROM community_posts;
  DELETE FROM photo_cards;
  DELETE FROM photo_tags;
  DELETE FROM ai_chat_message;
  DELETE FROM ai_chat_session;
  DELETE FROM ai_write_proposal;
  DELETE FROM ai_proactive_log;
  DELETE FROM notification_log;

  -- ── 2) 고객 25명 (등급 = 실제 구매횟수와 정합) ───────────
  INSERT INTO customers(user_id,name,phone,grade_id,grade_locked,gender,memo,created_at,updated_at) VALUES
    (v_uid,'김민지','010-9101-2211',g_vip,false,'female','격주 화병 정기 교체, 단골 중 단골','2026-01-08 10:00+09','2026-01-08 10:00+09') RETURNING id INTO c01;
  INSERT INTO customers(user_id,name,phone,grade_id,grade_locked,gender,memo,created_at,updated_at) VALUES
    (v_uid,'박상우','010-9102-3322',g_vip,false,'male','카페 블룸 사장님, 격주 납품(계좌이체)','2026-01-13 11:00+09','2026-01-13 11:00+09') RETURNING id INTO c02;
  INSERT INTO customers(user_id,name,phone,grade_id,grade_locked,gender,memo,created_at,updated_at) VALUES
    (v_uid,'윤세라','010-9103-4433',g_vip,false,'female','기념일을 잘 챙김, 객단가 높음','2026-01-17 14:00+09','2026-01-17 14:00+09') RETURNING id INTO c03;
  INSERT INTO customers(user_id,name,phone,grade_id,grade_locked,gender,memo,created_at,updated_at) VALUES
    (v_uid,'이서준','010-9104-5544',g_reg,false,'male','2월 프로포즈 성공, 6월 결혼 예정','2026-02-10 15:00+09','2026-02-10 15:00+09') RETURNING id INTO c04;
  INSERT INTO customers(user_id,name,phone,grade_id,grade_locked,gender,memo,created_at,updated_at) VALUES
    (v_uid,'최유나','010-9105-6655',g_reg,false,'female','6/14 본식 부케 예약 고객','2026-03-12 13:00+09','2026-03-12 13:00+09') RETURNING id INTO c05;
  INSERT INTO customers(user_id,name,phone,grade_id,grade_locked,gender,memo,created_at,updated_at) VALUES
    (v_uid,'정하늘','010-9106-7766',g_reg,false,'female','작약 좋아하심','2026-01-24 12:00+09','2026-01-24 12:00+09') RETURNING id INTO c06;
  INSERT INTO customers(user_id,name,phone,grade_id,grade_locked,gender,memo,created_at,updated_at) VALUES
    (v_uid,'강도윤','010-9107-8877',g_reg,false,'male',NULL,'2026-02-13 16:00+09','2026-02-13 16:00+09') RETURNING id INTO c07;
  INSERT INTO customers(user_id,name,phone,grade_id,grade_locked,gender,memo,created_at,updated_at) VALUES
    (v_uid,'한지민','010-9108-9988',g_reg,true,'female','사장님 지인 — 등급 수동 고정','2026-01-09 10:30+09','2026-01-09 10:30+09') RETURNING id INTO c08;
  INSERT INTO customers(user_id,name,phone,grade_id,grade_locked,gender,memo,created_at,updated_at) VALUES
    (v_uid,'오민석','010-9109-1100',g_reg,false,'male',NULL,'2026-01-31 17:00+09','2026-01-31 17:00+09') RETURNING id INTO c09;
  INSERT INTO customers(user_id,name,phone,grade_id,grade_locked,gender,memo,created_at,updated_at) VALUES
    (v_uid,'서예린','010-9110-2211',g_new,false,'female','인스타 보고 방문','2026-04-10 11:00+09','2026-04-10 11:00+09') RETURNING id INTO c10;
  INSERT INTO customers(user_id,name,phone,grade_id,grade_locked,gender,memo,created_at,updated_at) VALUES
    (v_uid,'김태호','010-9111-3322',g_new,false,'male',NULL,'2026-02-16 12:00+09','2026-02-16 12:00+09') RETURNING id INTO c11;
  INSERT INTO customers(user_id,name,phone,grade_id,grade_locked,gender,memo,created_at,updated_at) VALUES
    (v_uid,'배수지','010-9112-4433',g_new,false,'female',NULL,'2026-03-12 14:00+09','2026-03-12 14:00+09') RETURNING id INTO c12;
  INSERT INTO customers(user_id,name,phone,grade_id,grade_locked,gender,memo,created_at,updated_at) VALUES
    (v_uid,'신동혁','010-9113-5544',g_new,false,'male',NULL,'2026-01-15 13:00+09','2026-01-15 13:00+09') RETURNING id INTO c13;
  INSERT INTO customers(user_id,name,phone,grade_id,grade_locked,gender,memo,created_at,updated_at) VALUES
    (v_uid,'임나연','010-9114-6655',g_new,false,'female',NULL,'2026-02-11 11:00+09','2026-02-11 11:00+09') RETURNING id INTO c14;
  INSERT INTO customers(user_id,name,phone,grade_id,grade_locked,gender,memo,created_at,updated_at) VALUES
    (v_uid,'장우진','010-9115-7766',g_new,false,'male','거래처 개업 화분 주문','2026-03-20 13:00+09','2026-03-20 13:00+09') RETURNING id INTO c15;
  INSERT INTO customers(user_id,name,phone,grade_id,grade_locked,gender,memo,created_at,updated_at) VALUES
    (v_uid,'송채원','010-9116-8877',g_new,false,'female',NULL,'2026-04-22 16:00+09','2026-04-22 16:00+09') RETURNING id INTO c16;
  INSERT INTO customers(user_id,name,phone,grade_id,grade_locked,gender,memo,created_at,updated_at) VALUES
    (v_uid,'문준영','010-9117-9988',g_new,false,'male',NULL,'2026-02-20 11:00+09','2026-02-20 11:00+09') RETURNING id INTO c17;
  INSERT INTO customers(user_id,name,phone,grade_id,grade_locked,gender,memo,created_at,updated_at) VALUES
    (v_uid,'황보라','010-9118-1212',g_new,false,'female',NULL,'2026-05-05 11:00+09','2026-05-05 11:00+09') RETURNING id INTO c18;
  INSERT INTO customers(user_id,name,phone,grade_id,grade_locked,gender,memo,created_at,updated_at) VALUES
    (v_uid,'안성민','010-9119-2323',g_new,false,'male',NULL,'2026-06-04 10:30+09','2026-06-04 10:30+09') RETURNING id INTO c19;
  INSERT INTO customers(user_id,name,phone,grade_id,grade_locked,gender,memo,created_at,updated_at) VALUES
    (v_uid,'유다은','010-9120-3434',g_new,false,'female','사무실 화병 정기 검토 중','2026-03-06 15:00+09','2026-03-06 15:00+09') RETURNING id INTO c20;
  INSERT INTO customers(user_id,name,phone,grade_id,grade_locked,gender,memo,created_at,updated_at) VALUES
    (v_uid,'조현우','010-9121-4545',g_new,false,'male',NULL,'2026-05-09 12:00+09','2026-05-09 12:00+09') RETURNING id INTO c21;
  INSERT INTO customers(user_id,name,phone,grade_id,grade_locked,gender,memo,created_at,updated_at) VALUES
    (v_uid,'노지원','010-9122-5656',g_new,false,'female',NULL,'2026-01-21 14:00+09','2026-01-21 14:00+09') RETURNING id INTO c22;
  INSERT INTO customers(user_id,name,phone,grade_id,grade_locked,gender,memo,created_at,updated_at) VALUES
    (v_uid,'권혁수','010-9123-6767',g_new,false,'male','개업 화분 견적 문의만 한 상태','2026-06-08 10:00+09','2026-06-08 10:00+09') RETURNING id INTO c23;
  INSERT INTO customers(user_id,name,phone,grade_id,grade_locked,gender,memo,created_at,updated_at) VALUES
    (v_uid,'백서연','010-9124-7878',g_new,false,'female',NULL,'2026-06-01 11:00+09','2026-06-01 11:00+09') RETURNING id INTO c24;
  INSERT INTO customers(user_id,name,phone,grade_id,grade_locked,gender,memo,created_at,updated_at) VALUES
    (v_uid,'마동수','010-9125-8989',g_blk,true,'male','노쇼 2회 — 선결제만 받기','2026-01-10 13:00+09','2026-01-10 13:00+09') RETURNING id INTO c25;

  -- ── 3) 예약→매출 전환 완료 24건 (sale + completed reservation) ──
  -- 1월
  PERFORM pg_temp.fres_done(v_uid,'2026-01-09','11:00','vase',60000,'card','kakaotalk',c08,'신년 화병 픽업','거실 화병용');
  PERFORM pg_temp.fres_done(v_uid,'2026-01-13','15:00','vase',75000,'transfer','phone',c02,'카페 납품 (1월 2주)','카페 블룸 정기 납품');
  PERFORM pg_temp.fres_done(v_uid,'2026-01-17','14:00','special_bouquet',110000,'card','kakaotalk',c03,'결혼기념일 꽃다발','화이트&그린 톤',true);
  PERFORM pg_temp.fres_done(v_uid,'2026-01-24','13:00','basic_bouquet',48000,'naverpay','naver_booking',c06,'생일 선물 픽업',NULL);
  -- 2월
  PERFORM pg_temp.fres_done(v_uid,'2026-02-12','10:30','mini_bouquet',28000,'card','kakaotalk',c06,'동생 졸업식 꽃다발','학사모 픽 추가');
  PERFORM pg_temp.fres_done(v_uid,'2026-02-14','17:00','proposal_bouquet',180000,'card','phone',c04,'프로포즈 부케','레드 장미 100송이',true);
  PERFORM pg_temp.fres_done(v_uid,'2026-02-16','12:00','basic_bouquet',45000,'card','naver_booking',c11,'졸업식 꽃다발',NULL);
  PERFORM pg_temp.fres_done(v_uid,'2026-02-20','11:00','mini_bouquet',30000,'cash','road',c17,'학위수여식 꽃다발',NULL);
  -- 3월
  PERFORM pg_temp.fres_done(v_uid,'2026-03-12','14:00','basket',85000,'naverpay','naver_booking',c12,'화이트데이 바구니','사탕 동봉',true);
  PERFORM pg_temp.fres_done(v_uid,'2026-03-14','15:00','basket',95000,'card','kakaotalk',c03,'화이트데이 선물',NULL);
  PERFORM pg_temp.fres_done(v_uid,'2026-03-14','16:30','medium_bouquet',60000,'card','naver_booking',c05,'화이트데이 꽃다발',NULL);
  PERFORM pg_temp.fres_done(v_uid,'2026-03-20','13:00','potted_plant',55000,'transfer','phone',c15,'개업 축하 화분','금전수');
  -- 4월
  PERFORM pg_temp.fres_done(v_uid,'2026-04-09','15:00','basic_bouquet',50000,'card','kakaotalk',c07,'아내 생일 꽃다발','파스텔 톤');
  PERFORM pg_temp.fres_done(v_uid,'2026-04-10','11:00','medium_bouquet',62000,'naverpay','naver_booking',c10,'집들이 꽃다발',NULL,true);
  PERFORM pg_temp.fres_done(v_uid,'2026-04-14','14:00','vase',58000,'card','kakaotalk',c20,'사무실 화병',NULL);
  PERFORM pg_temp.fres_done(v_uid,'2026-04-22','16:00','basket',90000,'transfer','phone',c16,'병문안 바구니','밝은 색 위주');
  -- 5월
  PERFORM pg_temp.fres_done(v_uid,'2026-05-07','10:00','basket',120000,'card','kakaotalk',c01,'어버이날 바구니','매년 주문하시는 단골',true);
  PERFORM pg_temp.fres_done(v_uid,'2026-05-05','11:00','basket',88000,'naverpay','naver_booking',c18,'어버이날 — 시부모님',NULL);
  PERFORM pg_temp.fres_done(v_uid,'2026-05-08','13:00','basket',130000,'card','phone',c03,'어버이날 — 양가','바구니 2개 묶음',true);
  PERFORM pg_temp.fres_done(v_uid,'2026-05-14','15:00','special_bouquet',110000,'card','kakaotalk',c12,'로즈데이 꽃다발','레드 장미');
  -- 6월
  PERFORM pg_temp.fres_done(v_uid,'2026-06-01','11:00','basic_bouquet',47000,'card','naver_booking',c24,'취업 축하 꽃다발',NULL);
  PERFORM pg_temp.fres_done(v_uid,'2026-06-03','14:00','photo_bouquet',150000,'transfer','phone',c05,'웨딩 촬영 부케','본식 전 스냅용',true);
  PERFORM pg_temp.fres_done(v_uid,'2026-06-04','10:30','potted_plant',45000,'card','road',c19,'사무실 개업 화분',NULL);
  PERFORM pg_temp.fres_done(v_uid,'2026-06-08','15:00','medium_bouquet',65000,'naverpay','kakaotalk',c18,'부모님 결혼기념일',NULL);

  -- ── 4) 고객 연결 매출 (직접 판매 73건) ───────────────────
  -- 1월 (8)
  PERFORM pg_temp.fsale(v_uid,'2026-01-08','vase',60000,'transfer','kakaotalk',c01,'정기 화병 교체');
  PERFORM pg_temp.fsale(v_uid,'2026-01-22','vase',60000,'transfer','kakaotalk',c01,'정기 화병 교체');
  PERFORM pg_temp.fsale(v_uid,'2026-01-27','vase',75000,'transfer','phone',c02,'카페 납품');
  PERFORM pg_temp.fsale(v_uid,'2026-01-30','preserved',95000,'card','kakaotalk',c03,'선물용 프리저브드',false,true);
  PERFORM pg_temp.fsale(v_uid,'2026-01-15','basic_bouquet',42000,'card','road',c13,NULL);
  PERFORM pg_temp.fsale(v_uid,'2026-01-21','mini_bouquet',26000,'cash','road',c22,NULL);
  PERFORM pg_temp.fsale(v_uid,'2026-01-31','medium_bouquet',58000,'card','kakaotalk',c09,NULL);
  PERFORM pg_temp.fsale(v_uid,'2026-01-10','basic_bouquet',45000,'cash','road',c25,'현장 결제');
  -- 2월 (10)
  PERFORM pg_temp.fsale(v_uid,'2026-02-05','vase',60000,'transfer','kakaotalk',c01,'정기 화병 교체');
  PERFORM pg_temp.fsale(v_uid,'2026-02-19','vase',60000,'transfer','kakaotalk',c01,'정기 화병 교체');
  PERFORM pg_temp.fsale(v_uid,'2026-02-10','vase',75000,'transfer','phone',c02,'카페 납품');
  PERFORM pg_temp.fsale(v_uid,'2026-02-24','basic_bouquet',70000,'transfer','phone',c02,'카페 행사용 테이블 꽃');
  PERFORM pg_temp.fsale(v_uid,'2026-02-14','special_bouquet',120000,'card','kakaotalk',c03,'발렌타인',false,true);
  PERFORM pg_temp.fsale(v_uid,'2026-02-13','basic_bouquet',46000,'card','naver_booking',c07,'졸업식 가족 모임');
  PERFORM pg_temp.fsale(v_uid,'2026-02-27','mini_bouquet',30000,'card','kakaotalk',c08,NULL);
  PERFORM pg_temp.fsale(v_uid,'2026-02-18','basket',78000,'naverpay','naver_booking',c09,NULL);
  PERFORM pg_temp.fsale(v_uid,'2026-02-11','mini_bouquet',25000,'cash','road',c14,'졸업 시즌');
  PERFORM pg_temp.fsale(v_uid,'2026-02-07','basic_bouquet',45000,NULL,'kakaotalk',c25,'미수 — 연락 두절',true);
  -- 3월 (11)
  PERFORM pg_temp.fsale(v_uid,'2026-03-05','vase',60000,'transfer','kakaotalk',c01,'정기 화병 교체');
  PERFORM pg_temp.fsale(v_uid,'2026-03-19','vase',60000,'transfer','kakaotalk',c01,'정기 화병 교체');
  PERFORM pg_temp.fsale(v_uid,'2026-03-10','vase',75000,'transfer','phone',c02,'카페 납품');
  PERFORM pg_temp.fsale(v_uid,'2026-03-24','vase',75000,'transfer','phone',c02,'카페 납품');
  PERFORM pg_temp.fsale(v_uid,'2026-03-28','basic_bouquet',52000,'card','kakaotalk',c03,NULL);
  PERFORM pg_temp.fsale(v_uid,'2026-03-21','basic_bouquet',48000,'card','kakaotalk',c04,'여자친구 생일');
  PERFORM pg_temp.fsale(v_uid,'2026-03-07','medium_bouquet',68000,'card','naver_booking',c06,'봄 꽃다발');
  PERFORM pg_temp.fsale(v_uid,'2026-03-02','mini_bouquet',28000,'cash','road',c07,NULL);
  PERFORM pg_temp.fsale(v_uid,'2026-03-26','basic_bouquet',47000,'card','kakaotalk',c08,NULL);
  PERFORM pg_temp.fsale(v_uid,'2026-03-31','vase',58000,'naverpay','kakaotalk',c09,NULL);
  PERFORM pg_temp.fsale(v_uid,'2026-03-06','mini_bouquet',27000,'card','road',c20,NULL);
  -- 4월 (13)
  PERFORM pg_temp.fsale(v_uid,'2026-04-02','vase',60000,'transfer','kakaotalk',c01,'정기 화병 교체');
  PERFORM pg_temp.fsale(v_uid,'2026-04-16','vase',60000,'transfer','kakaotalk',c01,'정기 화병 교체');
  PERFORM pg_temp.fsale(v_uid,'2026-04-30','vase',60000,'transfer','kakaotalk',c01,'정기 화병 교체');
  PERFORM pg_temp.fsale(v_uid,'2026-04-07','vase',75000,'transfer','phone',c02,'카페 납품');
  PERFORM pg_temp.fsale(v_uid,'2026-04-21','vase',75000,'transfer','phone',c02,'카페 납품');
  PERFORM pg_temp.fsale(v_uid,'2026-04-17','medium_bouquet',65000,'card','kakaotalk',c03,'지인 생일');
  PERFORM pg_temp.fsale(v_uid,'2026-04-25','preserved',88000,'card','kakaotalk',c03,NULL);
  PERFORM pg_temp.fsale(v_uid,'2026-04-11','mini_bouquet',28000,'card','road',c04,NULL);
  PERFORM pg_temp.fsale(v_uid,'2026-04-04','basic_bouquet',50000,'naverpay','naver_booking',c05,NULL);
  PERFORM pg_temp.fsale(v_uid,'2026-04-18','medium_bouquet',66000,'card','kakaotalk',c06,NULL);
  PERFORM pg_temp.fsale(v_uid,'2026-04-24','group',220000,NULL,'phone',c09,'교회 행사 단체 주문 — 5월 초 정산 예정',true);
  PERFORM pg_temp.fsale(v_uid,'2026-04-03','basic_bouquet',44000,'card','naver_booking',c14,NULL);
  PERFORM pg_temp.fsale(v_uid,'2026-04-29','basic_bouquet',45000,NULL,'kakaotalk',c22,'지인 — 다음 방문 때 정산',true);
  -- 5월 (19)
  PERFORM pg_temp.fsale(v_uid,'2026-05-14','vase',60000,'transfer','kakaotalk',c01,'정기 화병 교체');
  PERFORM pg_temp.fsale(v_uid,'2026-05-28','vase',60000,'transfer','kakaotalk',c01,'정기 화병 교체');
  PERFORM pg_temp.fsale(v_uid,'2026-05-12','vase',75000,'transfer','phone',c02,'카페 납품');
  PERFORM pg_temp.fsale(v_uid,'2026-05-26','vase',80000,NULL,'phone',c02,'카페 납품 — 월말 합산 정산',true);
  PERFORM pg_temp.fsale(v_uid,'2026-05-17','medium_bouquet',70000,'card','kakaotalk',c03,'로즈데이 답례');
  PERFORM pg_temp.fsale(v_uid,'2026-05-08','basket',95000,'card','phone',c04,'어버이날 — 양가 부모님',false,true);
  PERFORM pg_temp.fsale(v_uid,'2026-05-30','basic_bouquet',52000,'card','kakaotalk',c04,'상견례 답례 꽃다발');
  PERFORM pg_temp.fsale(v_uid,'2026-05-07','basket',90000,'naverpay','naver_booking',c05,'어버이날');
  PERFORM pg_temp.fsale(v_uid,'2026-05-21','bridal_bouquet',250000,'transfer','phone',c05,'6/14 본식 부케 — 사전 결제');
  PERFORM pg_temp.fsale(v_uid,'2026-05-15','basic_bouquet',55000,'card','kakaotalk',c06,'스승의날 — 카네이션 믹스');
  PERFORM pg_temp.fsale(v_uid,'2026-05-08','basket',85000,'card','naver_booking',c07,'어버이날');
  PERFORM pg_temp.fsale(v_uid,'2026-05-06','basket',82000,'card','kakaotalk',c08,'어버이날');
  PERFORM pg_temp.fsale(v_uid,'2026-05-29','mini_bouquet',28000,'cash','road',c08,NULL);
  PERFORM pg_temp.fsale(v_uid,'2026-05-22','medium_bouquet',60000,'naverpay','kakaotalk',c09,NULL);
  PERFORM pg_temp.fsale(v_uid,'2026-05-07','basket',86000,'card','naver_booking',c10,'어버이날',false,true);
  PERFORM pg_temp.fsale(v_uid,'2026-05-08','basket',80000,NULL,'kakaotalk',c11,'어버이날 — 입금 예정',true);
  PERFORM pg_temp.fsale(v_uid,'2026-05-16','basic_bouquet',47000,'card','road',c20,NULL);
  PERFORM pg_temp.fsale(v_uid,'2026-05-09','mini_bouquet',30000,'cash','road',c21,'카네이션 잔여 물량');
  PERFORM pg_temp.fsale(v_uid,'2026-05-30','wreath',180000,NULL,'phone',c16,'개업 화환 — 미수',true);
  -- 6월 (12)
  PERFORM pg_temp.fsale(v_uid,'2026-06-04','vase',60000,'transfer','kakaotalk',c01,'정기 화병 교체');
  PERFORM pg_temp.fsale(v_uid,'2026-06-02','vase',75000,'transfer','phone',c02,'카페 납품');
  PERFORM pg_temp.fsale(v_uid,'2026-06-09','vase',75000,NULL,'phone',c02,'카페 납품 — 월말 정산',true);
  PERFORM pg_temp.fsale(v_uid,'2026-06-05','basic_bouquet',50000,'card','kakaotalk',c03,NULL);
  PERFORM pg_temp.fsale(v_uid,'2026-06-07','photo_bouquet',140000,'transfer','phone',c04,'웨딩 스냅 촬영');
  PERFORM pg_temp.fsale(v_uid,'2026-06-10','mini_bouquet',30000,'card','road',c04,'청첩장 모임');
  PERFORM pg_temp.fsale(v_uid,'2026-06-08','vase',60000,'card','kakaotalk',c05,'신혼집 화병');
  PERFORM pg_temp.fsale(v_uid,'2026-06-06','medium_bouquet',68000,'card','naver_booking',c06,'작약 다발',false,true);
  PERFORM pg_temp.fsale(v_uid,'2026-06-05','basic_bouquet',48000,NULL,'kakaotalk',c07,'미수 — 주말 입금 예정',true);
  PERFORM pg_temp.fsale(v_uid,'2026-06-09','basic_bouquet',46000,'naverpay','naver_booking',c14,NULL);
  PERFORM pg_temp.fsale(v_uid,'2026-06-02','basic_bouquet',45000,'card','kakaotalk',c10,NULL);
  PERFORM pg_temp.fsale(v_uid,'2026-06-10','mini_bouquet',27000,'card','road',c20,NULL);
  -- 6월 추가분 (1~10일)
  PERFORM pg_temp.fsale(v_uid,'2026-06-01','medium_bouquet',62000,'card','kakaotalk',c03,'초여름 꽃다발',false,true);
  PERFORM pg_temp.fsale(v_uid,'2026-06-03','basket',88000,'naverpay','naver_booking',c08,'집들이 선물');
  PERFORM pg_temp.fsale(v_uid,'2026-06-04','mini_bouquet',28000,'cash','road',c13,NULL);
  PERFORM pg_temp.fsale(v_uid,'2026-06-05','vase',75000,'transfer','phone',c02,'카페 납품');
  PERFORM pg_temp.fsale(v_uid,'2026-06-06','preserved',92000,'card','kakaotalk',c10,'기념일 프리저브드',false,true);
  PERFORM pg_temp.fsale(v_uid,'2026-06-07','basic_bouquet',49000,'card','naver_booking',c12,NULL);
  PERFORM pg_temp.fsale(v_uid,'2026-06-08','large_bouquet',98000,'card','phone',c05,'신부 부모님 감사 꽃다발');
  PERFORM pg_temp.fsale(v_uid,'2026-06-08','mini_bouquet',26000,'cash','road',c22,NULL);
  PERFORM pg_temp.fsale(v_uid,'2026-06-09','basket',82000,'card','kakaotalk',c07,'부모님 생신');
  PERFORM pg_temp.fsale(v_uid,'2026-06-09','potted_plant',52000,'card','road',c15,'사무실 화분');
  PERFORM pg_temp.fsale(v_uid,'2026-06-10','medium_bouquet',60000,'naverpay','naver_booking',c06,'작약 다발',false,true);
  PERFORM pg_temp.fsale(v_uid,'2026-06-10','basic_bouquet',47000,NULL,'kakaotalk',c09,'미수 — 다음 방문 시 정산',true);

  -- ── 5) 익명 워크인 매출 145건 (시즌 가중) ────────────────
  PERFORM pg_temp.fwalkins(v_uid,'2026-01-02','2026-01-31',20,'normal');
  PERFORM pg_temp.fwalkins(v_uid,'2026-02-01','2026-02-28',14,'normal');
  PERFORM pg_temp.fwalkins(v_uid,'2026-02-09','2026-02-20',18,'graduation');  -- 졸업식 주간
  PERFORM pg_temp.fwalkins(v_uid,'2026-03-01','2026-03-31',19,'normal');
  PERFORM pg_temp.fwalkins(v_uid,'2026-03-13','2026-03-14',6,'normal');       -- 화이트데이
  PERFORM pg_temp.fwalkins(v_uid,'2026-04-01','2026-04-30',17,'normal');
  PERFORM pg_temp.fwalkins(v_uid,'2026-05-01','2026-05-31',17,'normal');
  PERFORM pg_temp.fwalkins(v_uid,'2026-05-06','2026-05-08',18,'parents');     -- 어버이날
  PERFORM pg_temp.fwalkins(v_uid,'2026-05-14','2026-05-15',4,'normal');       -- 로즈데이·스승의날
  PERFORM pg_temp.fwalkins(v_uid,'2026-06-01','2026-06-10',12,'normal');

  -- ── 6) 미래 예약 13건 (6/11~6/30) + 취소 3건 ────────────
  PERFORM pg_temp.fres(v_uid,'2026-06-14','12:00',c05,'본식 부케 전달','웨딩홀 직접 전달 (강남)','confirmed',250000,'2026-06-13 09:00+09');
  PERFORM pg_temp.fres(v_uid,'2026-06-16','10:00',c02,'카페 납품','카페 블룸 정기','confirmed',80000,NULL);
  PERFORM pg_temp.fres(v_uid,'2026-06-17','15:00',c03,'기념일 꽃다발','핑크 톤 요청','confirmed',70000,NULL);
  PERFORM pg_temp.fres(v_uid,'2026-06-18','11:00',c01,'정기 화병 교체','거실 대형 화병','confirmed',60000,NULL);
  PERFORM pg_temp.fres(v_uid,'2026-06-19','14:00',c07,'부모님 생신 꽃바구니',NULL,'confirmed',85000,NULL);
  PERFORM pg_temp.fres(v_uid,'2026-06-20','17:00',c04,'기념일 서프라이즈 배달','신혼집 배송','confirmed',95000,'2026-06-19 10:00+09');
  PERFORM pg_temp.fres(v_uid,'2026-06-22','13:00',c08,'화병꽂이 픽업',NULL,'confirmed',65000,NULL);
  PERFORM pg_temp.fres(v_uid,'2026-06-26','11:00',c14,'돌잔치 테이블 꽃','10테이블 센터피스','confirmed',120000,'2026-06-25 09:00+09');
  PERFORM pg_temp.fres(v_uid,'2026-06-13','15:00',c23,'개업 화분 견적 상담','관엽 3종 견적 요청','pending',150000,NULL);
  PERFORM pg_temp.fres(v_uid,'2026-06-21','14:00',c20,'생일 꽃다발',NULL,'pending',55000,NULL);
  PERFORM pg_temp.fres(v_uid,'2026-06-24','16:00',c06,'작약 다발 픽업','핑크 작약 위주','pending',68000,NULL);
  PERFORM pg_temp.fres(v_uid,'2026-06-27','11:00',c10,'집들이 바구니',NULL,'pending',90000,NULL);
  PERFORM pg_temp.fres(v_uid,'2026-06-30','10:00',c16,'개업 화환','리본 문구 추후 전달','pending',180000,NULL);
  -- 6월 추가 예약분
  PERFORM pg_temp.fres(v_uid,'2026-06-12','11:00',c12,'생일 꽃다발 픽업','퍼플 톤 요청','confirmed',55000,'2026-06-11 10:00+09');
  PERFORM pg_temp.fres(v_uid,'2026-06-15','16:00',c03,'결혼기념일 꽃다발','매년 주문하시는 단골','confirmed',90000,'2026-06-14 10:00+09');
  PERFORM pg_temp.fres(v_uid,'2026-06-16','13:30',c05,'본식 신부 부케 리허설','6/14 본식 전 컨펌용','confirmed',80000,NULL);
  PERFORM pg_temp.fres(v_uid,'2026-06-23','10:00',c02,'카페 납품','카페 블룸 정기','confirmed',75000,NULL);
  PERFORM pg_temp.fres(v_uid,'2026-06-25','15:00',c10,'집들이 화분','관엽 2종','confirmed',70000,NULL);
  PERFORM pg_temp.fres(v_uid,'2026-06-18','17:00',c09,'프로포즈 꽃다발 상담','레드 장미 견적 문의','pending',120000,NULL);
  PERFORM pg_temp.fres(v_uid,'2026-06-28','14:00',c15,'사무실 개업 화환','로비 비치용','pending',150000,NULL);
  PERFORM pg_temp.fres(v_uid,'2026-06-29','11:00',c01,'정기 화병 교체','거실 대형 화병','confirmed',60000,NULL);
  -- 취소
  PERFORM pg_temp.fres(v_uid,'2026-05-02','11:00',c25,'꽃다발 예약','노쇼 — 연락 두절','cancelled',50000,NULL);
  PERFORM pg_temp.fres(v_uid,'2026-03-08','13:00',c11,'졸업 꽃다발 추가','일정 변경으로 취소','cancelled',45000,NULL);
  PERFORM pg_temp.fres(v_uid,'2026-06-07','15:00',c21,'꽃바구니','단순 변심 취소','cancelled',70000,NULL);

  -- ── 7) 고정비 템플릿 4종 + 자동생성분 연결 (1~6월) ───────
  INSERT INTO recurring_expenses(user_id,item_name,category_id,unit_price,quantity,payment_method_id,vendor,memo,
                                 frequency,interval_count,days_of_month,start_date,created_at,updated_at)
  SELECT v_uid,'매장 임대료',
         (SELECT id FROM label_settings WHERE user_id=v_uid AND domain='expense' AND kind='category' AND value='rent'),
         1200000,1,
         (SELECT id FROM label_settings WHERE user_id=v_uid AND domain='expense' AND kind='payment' AND value='transfer'),
         '건물주',NULL,'monthly',1,'{1}','2026-01-01','2025-12-28 10:00+09','2025-12-28 10:00+09'
  RETURNING id INTO r_rent;
  INSERT INTO recurring_expenses(user_id,item_name,category_id,unit_price,quantity,payment_method_id,vendor,memo,
                                 frequency,interval_count,days_of_month,start_date,created_at,updated_at)
  SELECT v_uid,'인터넷+POS 통신비',
         (SELECT id FROM label_settings WHERE user_id=v_uid AND domain='expense' AND kind='category' AND value='utilities'),
         55000,1,
         (SELECT id FROM label_settings WHERE user_id=v_uid AND domain='expense' AND kind='payment' AND value='card'),
         'KT',NULL,'monthly',1,'{5}','2026-01-05','2025-12-28 10:05+09','2025-12-28 10:05+09'
  RETURNING id INTO r_net;
  INSERT INTO recurring_expenses(user_id,item_name,category_id,unit_price,quantity,payment_method_id,vendor,memo,
                                 frequency,interval_count,days_of_month,start_date,created_at,updated_at)
  SELECT v_uid,'정수기 렌탈',
         (SELECT id FROM label_settings WHERE user_id=v_uid AND domain='expense' AND kind='category' AND value='maintenance'),
         32900,1,
         (SELECT id FROM label_settings WHERE user_id=v_uid AND domain='expense' AND kind='payment' AND value='card'),
         '코웨이',NULL,'monthly',1,'{15}','2026-01-15','2025-12-28 10:10+09','2025-12-28 10:10+09'
  RETURNING id INTO r_water;
  INSERT INTO recurring_expenses(user_id,item_name,category_id,unit_price,quantity,payment_method_id,vendor,memo,
                                 frequency,interval_count,days_of_month,start_date,created_at,updated_at)
  SELECT v_uid,'화재보험료',
         (SELECT id FROM label_settings WHERE user_id=v_uid AND domain='expense' AND kind='category' AND value='etc'),
         48000,1,
         (SELECT id FROM label_settings WHERE user_id=v_uid AND domain='expense' AND kind='payment' AND value='transfer'),
         '삼성화재',NULL,'monthly',1,'{20}','2026-01-20','2025-12-28 10:15+09','2025-12-28 10:15+09'
  RETURNING id INTO r_ins;

  -- 자동생성분 (오늘 2026-06-10 이전 발생분만): 임대료 6, 통신비 6, 렌탈 5, 보험 5
  FOR v_m IN 1..6 LOOP
    PERFORM pg_temp.fexp(v_uid, make_date(2026,v_m,1), '매장 임대료','rent',1200000,1,'transfer','건물주',NULL,r_rent);
    PERFORM pg_temp.fexp(v_uid, make_date(2026,v_m,5), '인터넷+POS 통신비','utilities',55000,1,'card','KT',NULL,r_net);
    IF v_m <= 5 THEN
      PERFORM pg_temp.fexp(v_uid, make_date(2026,v_m,15), '정수기 렌탈','maintenance',32900,1,'card','코웨이',NULL,r_water);
      PERFORM pg_temp.fexp(v_uid, make_date(2026,v_m,20), '화재보험료','etc',48000,1,'transfer','삼성화재',NULL,r_ins);
    END IF;
  END LOOP;

  -- ── 8) 변동 지출 64건 ─────────────────────────────────────
  -- 꽃 사입 (격주 화요일 정기 + 이벤트 사전 사입)
  PERFORM pg_temp.fexp(v_uid,'2026-01-06','주간 사입 — 장미·튤립','flower_purchase',35000,8,'transfer','양재 화훼공판장',NULL);
  PERFORM pg_temp.fexp(v_uid,'2026-01-20','주간 사입 — 라넌큘러스','flower_purchase',32000,7,'transfer','양재 화훼공판장',NULL);
  PERFORM pg_temp.fexp(v_uid,'2026-02-03','졸업 시즌 사전 사입','flower_purchase',38000,10,'transfer','양재 화훼공판장','졸업식 주간 대비');
  PERFORM pg_temp.fexp(v_uid,'2026-02-06','졸업식 물량 — 미니부케용','flower_purchase',28000,15,'transfer','양재 화훼공판장',NULL);
  PERFORM pg_temp.fexp(v_uid,'2026-02-12','졸업식 추가 사입','flower_purchase',26000,10,'transfer','양재 화훼공판장','물량 부족분 보충');
  PERFORM pg_temp.fexp(v_uid,'2026-02-17','주간 사입','flower_purchase',33000,8,'transfer','양재 화훼공판장',NULL);
  PERFORM pg_temp.fexp(v_uid,'2026-03-03','주간 사입 — 봄 소재','flower_purchase',34000,8,'transfer','양재 화훼공판장','튤립·프리지아');
  PERFORM pg_temp.fexp(v_uid,'2026-03-12','화이트데이 소재','flower_purchase',30000,6,'transfer','양재 화훼공판장',NULL);
  PERFORM pg_temp.fexp(v_uid,'2026-03-17','주간 사입','flower_purchase',31000,7,'transfer','양재 화훼공판장',NULL);
  PERFORM pg_temp.fexp(v_uid,'2026-03-31','주간 사입','flower_purchase',30000,7,'transfer','양재 화훼공판장',NULL);
  PERFORM pg_temp.fexp(v_uid,'2026-04-14','주간 사입','flower_purchase',32000,7,'transfer','양재 화훼공판장',NULL);
  PERFORM pg_temp.fexp(v_uid,'2026-04-28','주간 사입','flower_purchase',33000,7,'transfer','양재 화훼공판장','카네이션 시세 체크');
  PERFORM pg_temp.fexp(v_uid,'2026-05-01','카네이션 대량 사입','flower_purchase',22000,25,'transfer','양재 화훼공판장','어버이날 대비');
  PERFORM pg_temp.fexp(v_uid,'2026-05-04','카네이션 추가','flower_purchase',20000,12,'transfer','양재 화훼공판장',NULL);
  PERFORM pg_temp.fexp(v_uid,'2026-05-12','주간 사입 — 작약 포함','flower_purchase',42000,8,'transfer','양재 화훼공판장','작약 시즌 시작');
  PERFORM pg_temp.fexp(v_uid,'2026-05-13','작약 사입','flower_purchase',50000,5,'transfer','양재 화훼공판장',NULL);
  PERFORM pg_temp.fexp(v_uid,'2026-05-26','주간 사입','flower_purchase',35000,7,'transfer','양재 화훼공판장',NULL);
  PERFORM pg_temp.fexp(v_uid,'2026-05-29','웨딩 소재 선사입','flower_purchase',55000,4,'transfer','양재 화훼공판장','6월 본식 대비');
  PERFORM pg_temp.fexp(v_uid,'2026-06-05','부케 소재 추가','flower_purchase',48000,3,'transfer','양재 화훼공판장',NULL);
  PERFORM pg_temp.fexp(v_uid,'2026-06-09','주간 사입 — 웨딩 소재','flower_purchase',40000,8,'transfer','양재 화훼공판장',NULL);
  -- 공과금
  PERFORM pg_temp.fexp(v_uid,'2026-01-25','전기·수도요금','utilities',185000,1,'card',NULL,'겨울 난방');
  PERFORM pg_temp.fexp(v_uid,'2026-02-25','전기·수도요금','utilities',172000,1,'card',NULL,NULL);
  PERFORM pg_temp.fexp(v_uid,'2026-03-25','전기·수도요금','utilities',138000,1,'card',NULL,NULL);
  PERFORM pg_temp.fexp(v_uid,'2026-04-25','전기·수도요금','utilities',112000,1,'card',NULL,NULL);
  PERFORM pg_temp.fexp(v_uid,'2026-05-25','전기·수도요금','utilities',98000,1,'card',NULL,NULL);
  PERFORM pg_temp.fexp(v_uid,'2026-06-08','전기·수도요금','utilities',89000,1,'card',NULL,NULL);
  -- 부자재
  PERFORM pg_temp.fexp(v_uid,'2026-01-14','포장지·리본','supplies',52000,1,'card','부자재상',NULL);
  PERFORM pg_temp.fexp(v_uid,'2026-02-04','졸업 포장지 대량','supplies',95000,1,'card','부자재상','졸업식 주간 대비');
  PERFORM pg_temp.fexp(v_uid,'2026-02-19','리본 추가','supplies',38000,1,'card','부자재상',NULL);
  PERFORM pg_temp.fexp(v_uid,'2026-03-11','오아시스·셀로판','supplies',47000,1,'card','부자재상',NULL);
  PERFORM pg_temp.fexp(v_uid,'2026-04-08','포장 부자재','supplies',43000,1,'card','부자재상',NULL);
  PERFORM pg_temp.fexp(v_uid,'2026-05-02','어버이날 리본·태그','supplies',88000,1,'card','부자재상','카네이션 포장용');
  PERFORM pg_temp.fexp(v_uid,'2026-05-16','부자재 보충','supplies',36000,1,'card','부자재상',NULL);
  PERFORM pg_temp.fexp(v_uid,'2026-05-27','글루건·철사','supplies',24000,1,'card','부자재상',NULL);
  PERFORM pg_temp.fexp(v_uid,'2026-06-04','포장지 보충','supplies',41000,1,'card','부자재상',NULL);
  -- 배송비 (퀵 묶음 정산)
  PERFORM pg_temp.fexp(v_uid,'2026-01-16','퀵 배송비 정산','delivery',9000,3,'cash',NULL,NULL);
  PERFORM pg_temp.fexp(v_uid,'2026-01-29','퀵 배송비 정산','delivery',9000,2,'cash',NULL,NULL);
  PERFORM pg_temp.fexp(v_uid,'2026-02-13','퀵 배송비 정산','delivery',9000,5,'cash',NULL,'졸업식 주간');
  PERFORM pg_temp.fexp(v_uid,'2026-02-26','퀵 배송비 정산','delivery',9000,3,'cash',NULL,NULL);
  PERFORM pg_temp.fexp(v_uid,'2026-03-13','퀵 배송비 정산','delivery',9000,4,'cash',NULL,'화이트데이');
  PERFORM pg_temp.fexp(v_uid,'2026-03-27','퀵 배송비 정산','delivery',9000,2,'cash',NULL,NULL);
  PERFORM pg_temp.fexp(v_uid,'2026-04-10','퀵 배송비 정산','delivery',9000,3,'cash',NULL,NULL);
  PERFORM pg_temp.fexp(v_uid,'2026-04-24','퀵 배송비 정산','delivery',9000,2,'cash',NULL,NULL);
  PERFORM pg_temp.fexp(v_uid,'2026-05-08','퀵 배송비 정산','delivery',9000,7,'cash',NULL,'어버이날 배달');
  PERFORM pg_temp.fexp(v_uid,'2026-05-18','퀵 배송비 정산','delivery',9000,4,'cash',NULL,NULL);
  PERFORM pg_temp.fexp(v_uid,'2026-05-29','퀵 배송비 정산','delivery',9000,3,'cash',NULL,NULL);
  PERFORM pg_temp.fexp(v_uid,'2026-06-08','퀵 배송비 정산','delivery',9000,3,'cash',NULL,NULL);
  -- 광고비
  PERFORM pg_temp.fexp(v_uid,'2026-01-10','인스타 부스트','advertising',80000,1,'card','Meta',NULL);
  PERFORM pg_temp.fexp(v_uid,'2026-02-08','졸업 시즌 광고','advertising',150000,1,'card','Meta','졸업·발렌타인 타겟');
  PERFORM pg_temp.fexp(v_uid,'2026-03-10','인스타 부스트','advertising',80000,1,'card','Meta',NULL);
  PERFORM pg_temp.fexp(v_uid,'2026-04-12','인스타 부스트','advertising',70000,1,'card','Meta',NULL);
  PERFORM pg_temp.fexp(v_uid,'2026-05-02','가정의달 광고','advertising',160000,1,'card','Meta','어버이날 타겟');
  PERFORM pg_temp.fexp(v_uid,'2026-06-03','웨딩 시즌 광고','advertising',90000,1,'card','Meta',NULL);
  -- 인건비 (성수기 알바)
  PERFORM pg_temp.fexp(v_uid,'2026-02-13','졸업주간 알바 (2일)','labor',100000,2,'transfer',NULL,'포장 보조');
  PERFORM pg_temp.fexp(v_uid,'2026-02-20','졸업주간 알바 추가','labor',100000,1,'transfer',NULL,NULL);
  PERFORM pg_temp.fexp(v_uid,'2026-05-07','어버이날 알바 (2명)','labor',110000,2,'transfer',NULL,'포장·배달 보조');
  PERFORM pg_temp.fexp(v_uid,'2026-05-09','어버이날 알바 정산','labor',110000,1,'transfer',NULL,NULL);
  -- 관리·기타
  PERFORM pg_temp.fexp(v_uid,'2026-01-28','매장 청소 용역','maintenance',50000,1,'transfer',NULL,'월 1회');
  PERFORM pg_temp.fexp(v_uid,'2026-02-25','매장 청소 용역','maintenance',50000,1,'transfer',NULL,'월 1회');
  PERFORM pg_temp.fexp(v_uid,'2026-03-29','매장 청소 용역','maintenance',50000,1,'transfer',NULL,'월 1회');
  PERFORM pg_temp.fexp(v_uid,'2026-04-26','매장 청소 용역','maintenance',50000,1,'transfer',NULL,'월 1회');
  PERFORM pg_temp.fexp(v_uid,'2026-05-31','매장 청소 용역','maintenance',50000,1,'transfer',NULL,'월 1회');
  PERFORM pg_temp.fexp(v_uid,'2026-03-15','카드 단말기 수수료 정산','etc',28000,1,'card',NULL,NULL);
  PERFORM pg_temp.fexp(v_uid,'2026-05-10','카드 수수료 정산','etc',41000,1,'card',NULL,'어버이날 주간분');

  -- ── 9) 일정 29건 ─────────────────────────────────────────
  INSERT INTO schedules(user_id,title,start_date,end_date,color,memo,created_at,updated_at)
  SELECT v_uid,'화훼공판장 정기 사입',d,d,'#3b82f6','양재 화훼공판장',
         LEAST(d - 7, DATE '2026-06-09')::timestamptz + interval '9 hours',
         LEAST(d - 7, DATE '2026-06-09')::timestamptz + interval '9 hours'
  FROM unnest(ARRAY['2026-01-06','2026-01-20','2026-02-03','2026-02-17','2026-03-03','2026-03-17','2026-03-31',
                    '2026-04-14','2026-04-28','2026-05-12','2026-05-26','2026-06-09','2026-06-23']::date[]) AS d;
  INSERT INTO schedules(user_id,title,start_date,end_date,color,memo,created_at,updated_at) VALUES
    (v_uid,'졸업식 성수기','2026-02-09','2026-02-20','#f43f5e','미니부케 물량 준비, 알바 투입','2026-02-01 09:00+09','2026-02-01 09:00+09'),
    (v_uid,'어버이날 성수기','2026-05-05','2026-05-08','#f43f5e','카네이션 확보·알바 투입','2026-04-27 09:00+09','2026-04-27 09:00+09'),
    (v_uid,'웨딩 시즌','2026-06-01','2026-06-30','#f43f5e','부케 일정 관리','2026-05-25 09:00+09','2026-05-25 09:00+09'),
    (v_uid,'여름 휴가 (가게 휴무)','2026-06-29','2026-07-01','#10b981','공지 미리 올리기','2026-06-05 09:00+09','2026-06-05 09:00+09');
  INSERT INTO schedules(user_id,title,start_date,end_date,color,memo,created_at,updated_at)
  SELECT v_uid,'원데이 클래스',d,d,'#8b5cf6','꽃다발 클래스 (4인)',
         LEAST(d - 10, DATE '2026-06-09')::timestamptz + interval '10 hours',
         LEAST(d - 10, DATE '2026-06-09')::timestamptz + interval '10 hours'
  FROM unnest(ARRAY['2026-01-24','2026-02-28','2026-03-28','2026-04-25','2026-05-23','2026-06-27']::date[]) AS d;
  INSERT INTO schedules(user_id,title,start_date,end_date,color,memo,created_at,updated_at)
  SELECT v_uid,'정기 휴무',d,d,'#6b7280','월 1회 휴무',
         LEAST(d - 14, DATE '2026-06-09')::timestamptz + interval '9 hours',
         LEAST(d - 14, DATE '2026-06-09')::timestamptz + interval '9 hours'
  FROM unnest(ARRAY['2026-01-05','2026-02-02','2026-03-02','2026-04-06','2026-05-18','2026-06-01']::date[]) AS d;
  -- 이벤트·납품·점검 등 추가 일정
  INSERT INTO schedules(user_id,title,start_date,end_date,color,memo,created_at,updated_at) VALUES
    (v_uid,'발렌타인데이','2026-02-14','2026-02-14','#ec4899','초콜릿 동봉 꽃다발 준비','2026-02-07 09:00+09','2026-02-07 09:00+09'),
    (v_uid,'화이트데이','2026-03-14','2026-03-14','#ec4899','사탕 바구니 세트 준비','2026-03-07 09:00+09','2026-03-07 09:00+09'),
    (v_uid,'로즈데이','2026-05-14','2026-05-14','#ec4899','레드 장미 물량 확보','2026-05-07 09:00+09','2026-05-07 09:00+09'),
    (v_uid,'스승의날','2026-05-15','2026-05-15','#ec4899','카네이션 소량 추가','2026-05-08 09:00+09','2026-05-08 09:00+09'),
    (v_uid,'쇼케이스 냉매 점검','2026-03-21','2026-03-21','#f59e0b','기사 방문 14시','2026-03-14 09:00+09','2026-03-14 09:00+09'),
    (v_uid,'쇼케이스 냉매 점검','2026-06-20','2026-06-20','#f59e0b','여름 대비 점검','2026-06-05 09:00+09','2026-06-05 09:00+09'),
    (v_uid,'최유나 본식 부케 납품','2026-06-14','2026-06-14','#8b5cf6','강남 웨딩홀 오전 전달','2026-05-21 09:00+09','2026-05-21 09:00+09'),
    (v_uid,'돌잔치 테이블 꽃 세팅','2026-06-26','2026-06-26','#8b5cf6','10테이블 센터피스','2026-06-10 09:00+09','2026-06-10 09:00+09'),
    (v_uid,'분기 재고 정리','2026-03-30','2026-03-31','#0ea5e9','부자재·화기 재고 점검','2026-03-23 09:00+09','2026-03-23 09:00+09'),
    (v_uid,'분기 재고 정리','2026-06-29','2026-06-30','#0ea5e9','상반기 마감 정산','2026-06-08 09:00+09','2026-06-08 09:00+09');

  -- ── 10) 커뮤니티: 글 12 + 댓글 27(삭제 1 포함) + 좋아요 18 ──
  -- 카운트(like_count/comment_count)는 아래 실데이터와 수기로 일치시킴 (검증 쿼리로 재확인)
  INSERT INTO community_posts(author_user_id,category,title,content,content_text,is_pinned,like_count,comment_count,created_at,updated_at) VALUES
    (v_uid,'notice','플로리 커뮤니티 이용 안내',
     '{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"꽃집 사장님들을 위한 공간입니다. 서로 존중하는 말로 소통해요."}]},{"type":"paragraph","content":[{"type":"text","text":"거래처 단가 등 민감한 정보는 비밀댓글 기능을 활용해 주세요."}]}]}'::jsonb,
     '꽃집 사장님들을 위한 공간입니다. 서로 존중하는 말로 소통해요. 거래처 단가 등 민감한 정보는 비밀댓글 기능을 활용해 주세요.',
     true,2,0,'2026-01-05 10:00+09','2026-01-05 10:00+09') RETURNING id INTO p1;
  INSERT INTO community_posts(author_user_id,category,title,content,content_text,like_count,comment_count,created_at,updated_at) VALUES
    (v_u1,'question','네이버 예약 수수료 다들 어떻게 하세요?',
     '{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"네이버예약 비중이 점점 늘어나는데 수수료가 부담되네요. 가격을 올려야 할지, 자체 채널을 키워야 할지 고민입니다."}]}]}'::jsonb,
     '네이버예약 비중이 점점 늘어나는데 수수료가 부담되네요. 가격을 올려야 할지, 자체 채널을 키워야 할지 고민입니다.',
     2,3,'2026-01-19 21:00+09','2026-01-19 21:00+09') RETURNING id INTO p2;
  INSERT INTO community_posts(author_user_id,category,title,content,content_text,like_count,comment_count,created_at,updated_at) VALUES
    (v_u8,'knowledge','겨울철 절화 수명 늘리는 보관 온도',
     '{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"쇼케이스 5도 유지가 기본인데, 입고 직후 2시간은 8도 정도로 두고 천천히 내리면 냉해가 덜합니다."}]},{"type":"paragraph","content":[{"type":"text","text":"특히 튤립은 급랭하면 고개가 꺾여요."}]}]}'::jsonb,
     '쇼케이스 5도 유지가 기본인데, 입고 직후 2시간은 8도 정도로 두고 천천히 내리면 냉해가 덜합니다. 특히 튤립은 급랭하면 고개가 꺾여요.',
     2,1,'2026-01-27 20:30+09','2026-01-27 20:30+09') RETURNING id INTO p3;
  INSERT INTO community_posts(author_user_id,category,title,content,content_text,like_count,comment_count,created_at,updated_at) VALUES
    (v_u1,'daily','발렌타인+졸업 겹주간 다들 생존하셨나요',
     '{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"졸업식이랑 발렌타인이 겹치니 새벽 사입을 3번이나 갔네요. 손목이 남아나질 않습니다 ㅠㅠ"}]}]}'::jsonb,
     '졸업식이랑 발렌타인이 겹치니 새벽 사입을 3번이나 갔네요. 손목이 남아나질 않습니다 ㅠㅠ',
     1,2,'2026-02-15 22:00+09','2026-02-15 22:00+09') RETURNING id INTO p5;
  INSERT INTO community_posts(author_user_id,category,title,content,content_text,like_count,comment_count,created_at,updated_at) VALUES
    (v_uid,'daily','졸업식 주간 후기... 다리가 후들거리네요',
     '{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"2주 내내 미니부케만 백 개 넘게 만든 것 같아요. 그래도 학사모 쓴 손님들 보면 뿌듯합니다."}]}]}'::jsonb,
     '2주 내내 미니부케만 백 개 넘게 만든 것 같아요. 그래도 학사모 쓴 손님들 보면 뿌듯합니다.',
     2,3,'2026-02-20 21:30+09','2026-02-20 21:30+09') RETURNING id INTO p4;
  INSERT INTO community_posts(author_user_id,category,title,content,content_text,like_count,comment_count,created_at,updated_at) VALUES
    (v_uid,'question','포스기 추천 부탁드려요 (카드 수수료)',
     '{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"단말기 교체 시기인데 수수료랑 정산 주기 기준으로 추천 부탁드립니다. 키오스크까지는 필요 없을 것 같아요."}]}]}'::jsonb,
     '단말기 교체 시기인데 수수료랑 정산 주기 기준으로 추천 부탁드립니다. 키오스크까지는 필요 없을 것 같아요.',
     1,3,'2026-03-09 19:00+09','2026-03-09 19:00+09') RETURNING id INTO p6;
  INSERT INTO community_posts(author_user_id,category,title,content,content_text,like_count,comment_count,created_at,updated_at) VALUES
    (v_u8,'review','부자재 도매 ○○상사 후기 — 리본 질이 좋아요',
     '{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"리본 발색이 좋고 최소 주문 수량이 낮아서 소규모 매장에 괜찮습니다. 배송은 2~3일 걸려요."}]}]}'::jsonb,
     '리본 발색이 좋고 최소 주문 수량이 낮아서 소규모 매장에 괜찮습니다. 배송은 2~3일 걸려요.',
     2,1,'2026-03-24 20:00+09','2026-03-24 20:00+09') RETURNING id INTO p7;
  INSERT INTO community_posts(author_user_id,category,title,content,content_text,like_count,comment_count,created_at,updated_at) VALUES
    (v_u1,'question','여름 앞두고 수국 물올림 팁 있을까요?',
     '{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"수국이 들어오기 시작했는데 반나절이면 고개를 숙이네요. 다들 물올림 어떻게 하시는지 궁금합니다."}]}]}'::jsonb,
     '수국이 들어오기 시작했는데 반나절이면 고개를 숙이네요. 다들 물올림 어떻게 하시는지 궁금합니다.',
     1,4,'2026-04-16 18:30+09','2026-04-16 18:30+09') RETURNING id INTO p8;
  INSERT INTO community_posts(author_user_id,category,title,content,content_text,like_count,comment_count,created_at,updated_at) VALUES
    (v_uid,'daily','어버이날 마감... 카네이션 완판했습니다',
     '{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"준비한 카네이션 전량 소진했어요. 알바 두 분 아니었으면 큰일 날 뻔했네요. 다들 고생하셨습니다!"}]}]}'::jsonb,
     '준비한 카네이션 전량 소진했어요. 알바 두 분 아니었으면 큰일 날 뻔했네요. 다들 고생하셨습니다!',
     2,3,'2026-05-08 22:00+09','2026-05-08 22:00+09') RETURNING id INTO p10;
  INSERT INTO community_posts(author_user_id,category,title,content,content_text,like_count,comment_count,created_at,updated_at) VALUES
    (v_uid,'knowledge','어버이날 카네이션 사입 타이밍 복기',
     '{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"올해는 D-7에 1차 대량, D-4에 2차 보충으로 갔는데 단가가 D-5부터 급등했습니다. 내년엔 1차를 D-8로 당기는 게 좋겠어요."}]},{"type":"paragraph","content":[{"type":"text","text":"참고로 올해 양재 기준 한 단 시세가 작년 대비 15%쯤 올랐습니다."}]}]}'::jsonb,
     '올해는 D-7에 1차 대량, D-4에 2차 보충으로 갔는데 단가가 D-5부터 급등했습니다. 내년엔 1차를 D-8로 당기는 게 좋겠어요. 참고로 올해 양재 기준 한 단 시세가 작년 대비 15%쯤 올랐습니다.',
     2,3,'2026-05-11 20:00+09','2026-05-11 20:00+09') RETURNING id INTO p9;
  INSERT INTO community_posts(author_user_id,category,title,content,content_text,like_count,comment_count,created_at,updated_at) VALUES
    (v_u1,'review','양재 공판장 ○○상회 단가 후기',
     '{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"거래처 단가 공유합니다. 장미 한 단 기준 시세보다 10% 저렴한데 등급이 들쭉날쭉해요. 직접 보고 고르실 분만 추천."}]}]}'::jsonb,
     '거래처 단가 공유합니다. 장미 한 단 기준 시세보다 10% 저렴한데 등급이 들쭉날쭉해요. 직접 보고 고르실 분만 추천.',
     0,1,'2026-05-27 21:00+09','2026-05-27 21:00+09') RETURNING id INTO p11;
  INSERT INTO community_posts(author_user_id,category,title,content,content_text,like_count,comment_count,created_at,updated_at) VALUES
    (v_uid,'etc','중고 화기(유리 화병 12개) 나눔합니다',
     '{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"매장 정리하면서 유리 화병 12개 나눔해요. 흠집 거의 없습니다. 선착순 댓글 주세요."}]}]}'::jsonb,
     '매장 정리하면서 유리 화병 12개 나눔해요. 흠집 거의 없습니다. 선착순 댓글 주세요.',
     1,2,'2026-06-03 14:00+09','2026-06-03 14:00+09') RETURNING id INTO p12;

  -- 댓글 (대댓글 3스레드 + soft delete 1)
  INSERT INTO community_comments(post_id,author_user_id,content,created_at) VALUES
    (p2,v_uid,'저는 수수료를 감안해서 네이버 노출가를 5% 올려뒀어요. 단골은 카톡 채널로 유도하고요.','2026-01-19 22:10+09') RETURNING id INTO cm;
  INSERT INTO community_comments(post_id,parent_id,author_user_id,content,created_at) VALUES
    (p2,cm,v_u1,'오 가격 차등 좋네요. 카톡 채널 쿠폰 같은 것도 쓰시나요?','2026-01-20 09:00+09');
  INSERT INTO community_comments(post_id,author_user_id,content,created_at) VALUES
    (p2,v_u8,'저는 노출용이라 생각하고 그냥 둡니다. 신규 유입은 확실히 네이버가 많아요.','2026-01-20 10:30+09');
  INSERT INTO community_comments(post_id,author_user_id,content,created_at) VALUES
    (p3,v_uid,'입고 직후 급랭 금지 꿀팁 감사합니다. 튤립 꺾임 원인을 이제 알았네요.','2026-01-28 09:30+09');
  INSERT INTO community_comments(post_id,author_user_id,content,created_at) VALUES
    (p5,v_uid,'겹주간 진짜 헬이죠... 저도 새벽 사입 3번 갔습니다.','2026-02-15 22:30+09');
  INSERT INTO community_comments(post_id,author_user_id,content,created_at,deleted_at) VALUES
    (p5,v_u8,'새벽 사입 보통 몇 시에 가세요?','2026-02-16 08:00+09','2026-02-16 08:20+09');
  INSERT INTO community_comments(post_id,author_user_id,content,created_at) VALUES
    (p5,v_u8,'다음 주까지는 버티셔야 합니다 화이팅','2026-02-16 08:25+09');
  INSERT INTO community_comments(post_id,author_user_id,content,created_at) VALUES
    (p4,v_u1,'고생하셨어요 ㅠㅠ 저도 오늘 막 마감했네요.','2026-02-20 22:00+09') RETURNING id INTO cm;
  INSERT INTO community_comments(post_id,parent_id,author_user_id,content,created_at) VALUES
    (p4,cm,v_uid,'내일 하루는 쉬려구요. 다들 푹 쉬세요!','2026-02-20 22:30+09');
  INSERT INTO community_comments(post_id,author_user_id,content,created_at) VALUES
    (p4,v_u8,'졸업 시즌 끝나면 바로 화이트데이... 꽃집에 비수기란 없네요','2026-02-21 09:00+09');
  INSERT INTO community_comments(post_id,author_user_id,content,created_at) VALUES
    (p6,v_u1,'저는 OO포스 씁니다. 수수료 1.9%에 D+2 정산이라 무난해요.','2026-03-09 20:00+09') RETURNING id INTO cm;
  INSERT INTO community_comments(post_id,parent_id,author_user_id,content,created_at) VALUES
    (p6,cm,v_uid,'D+2면 괜찮네요. 견적 받아보겠습니다 감사합니다!','2026-03-09 21:00+09');
  INSERT INTO community_comments(post_id,author_user_id,content,created_at) VALUES
    (p6,v_u8,'요즘은 무료 단말기 주는 대신 수수료 높은 데가 많으니 약정 꼭 확인하세요.','2026-03-10 08:40+09');
  INSERT INTO community_comments(post_id,author_user_id,content,created_at) VALUES
    (p7,v_uid,'저도 여기 리본 써요. 발색 진짜 좋습니다.','2026-03-24 21:00+09');
  INSERT INTO community_comments(post_id,author_user_id,content,created_at) VALUES
    (p8,v_u8,'끓는 물에 줄기 끝 10초 침지 후 바로 찬물에 꽂아보세요. 수국은 이게 제일 확실합니다.','2026-04-16 19:00+09') RETURNING id INTO cm;
  INSERT INTO community_comments(post_id,parent_id,author_user_id,content,created_at) VALUES
    (p8,cm,v_u1,'오늘 입고분에 바로 해볼게요. 감사합니다!','2026-04-16 19:30+09');
  INSERT INTO community_comments(post_id,parent_id,author_user_id,content,created_at) VALUES
    (p8,cm,v_u8,'추가로 잎은 최대한 떼 주세요. 증산 줄이는 게 핵심이에요.','2026-04-16 19:40+09');
  INSERT INTO community_comments(post_id,author_user_id,content,created_at) VALUES
    (p8,v_uid,'명반 가루 녹인 물에 꽂는 방법도 효과 봤습니다.','2026-04-16 20:10+09');
  INSERT INTO community_comments(post_id,author_user_id,content,created_at) VALUES
    (p9,v_u1,'D-7 1차 사입 동의합니다. 올해 D-5에 갔다가 단가 보고 놀랐어요.','2026-05-11 20:40+09') RETURNING id INTO cm;
  INSERT INTO community_comments(post_id,parent_id,author_user_id,content,created_at) VALUES
    (p9,cm,v_uid,'맞아요, 올해는 오름세가 유독 빨랐습니다.','2026-05-11 21:00+09');
  INSERT INTO community_comments(post_id,author_user_id,content,created_at) VALUES
    (p9,v_u8,'내년엔 D-10도 고려해 보세요. 보관만 가능하면 단가 차이가 꽤 큽니다.','2026-05-12 08:30+09');
  INSERT INTO community_comments(post_id,author_user_id,content,created_at) VALUES
    (p10,v_u1,'완판 축하드려요!! 저는 아직 30단 남았습니다 ㅋㅋ','2026-05-08 22:30+09') RETURNING id INTO cm;
  INSERT INTO community_comments(post_id,parent_id,author_user_id,content,created_at) VALUES
    (p10,cm,v_uid,'내일 출근길 직장인 대상으로 떨이 어떠세요? 저 작년에 그렇게 털었어요.','2026-05-08 23:00+09');
  INSERT INTO community_comments(post_id,author_user_id,content,created_at) VALUES
    (p10,v_u8,'고생 많으셨습니다. 저도 방금 마감했네요.','2026-05-08 23:10+09');
  INSERT INTO community_comments(post_id,author_user_id,content,is_secret,created_at) VALUES
    (p11,v_uid,'정보 감사합니다. 등급 편차는 직접 보고 사는 게 답이긴 하죠.',true,'2026-05-28 09:00+09');
  INSERT INTO community_comments(post_id,author_user_id,content,created_at) VALUES
    (p12,v_u1,'혹시 직접 수령인가요? 위치가 어디신지요!','2026-06-03 15:00+09') RETURNING id INTO cm;
  INSERT INTO community_comments(post_id,parent_id,author_user_id,content,created_at) VALUES
    (p12,cm,v_uid,'네, 매장 픽업입니다. 쪽지 드릴게요!','2026-06-03 15:30+09');

  -- 좋아요
  INSERT INTO community_likes(post_id,user_id,created_at) VALUES
    (p1,v_u1,'2026-01-06 09:00+09'),(p1,v_u8,'2026-01-06 10:00+09'),
    (p2,v_uid,'2026-01-19 22:00+09'),(p2,v_u8,'2026-01-20 10:00+09'),
    (p3,v_uid,'2026-01-28 09:00+09'),(p3,v_u1,'2026-01-28 11:00+09'),
    (p4,v_u1,'2026-02-20 22:00+09'),(p4,v_u8,'2026-02-21 09:00+09'),
    (p5,v_uid,'2026-02-15 22:20+09'),
    (p6,v_u1,'2026-03-09 20:00+09'),
    (p7,v_uid,'2026-03-24 21:00+09'),(p7,v_u1,'2026-03-25 08:00+09'),
    (p8,v_uid,'2026-04-16 20:00+09'),
    (p9,v_u1,'2026-05-11 20:30+09'),(p9,v_u8,'2026-05-12 08:00+09'),
    (p10,v_u1,'2026-05-08 22:20+09'),(p10,v_u8,'2026-05-08 23:00+09'),
    (p12,v_u8,'2026-06-04 09:00+09');

  -- 비정규화 카운트를 실데이터 기준으로 최종 동기화 (수기 값 검산 겸)
  -- updated_at 트리거가 NOW()로 덮지 않도록 잠시 비활성화
  ALTER TABLE community_posts DISABLE TRIGGER update_community_posts_updated_at;
  UPDATE community_posts p SET
    like_count    = (SELECT count(*) FROM community_likes l WHERE l.post_id = p.id),
    comment_count = (SELECT count(*) FROM community_comments c WHERE c.post_id = p.id AND c.deleted_at IS NULL),
    updated_at    = p.created_at;
  ALTER TABLE community_posts ENABLE TRIGGER update_community_posts_updated_at;

  RAISE NOTICE '시드 완료: user_id=% (매출 %건)', v_uid, (SELECT count(*) FROM sales WHERE user_id = v_uid);
END $seed$;

COMMIT;
