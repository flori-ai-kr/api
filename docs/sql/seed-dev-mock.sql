-- =============================================
-- 개발용 목(mock) 데이터 — 2026년 3~5월
-- =============================================
-- 로컬/개발 화면 확인용. 운영 적용 금지. all-tables-ddl.sql + 마이그레이션 적용 후 실행.
--
-- 사용법:
--   1) 아래 v_email 을 로그인하는 계정 이메일로 교체 (SELECT id,email FROM users; 로 확인)
--   2) psql "$DB_URL" -f docs/sql/seed-dev-mock.sql
--
-- 주의: 멱등 아님 — 두 번 실행하면 중복된다. 다시 깔려면 해당 user 데이터를 지우고 재실행.
-- 기본값 출처: DefaultDataSeeder(sale_categories/payment_methods 등) + 기존 Supabase 카테고리/채널.
-- =============================================
DO $$
DECLARE
  v_email   text := 'CHANGE_ME@example.com';  -- ← 로그인 계정 이메일로 교체
  v_uid     bigint;
  v_other   bigint;                            -- 비밀글 마스킹 데모용(다른 계정 있으면 자동 사용)
  c_kim bigint; c_lee bigint; c_park bigint; c_choi bigint; c_jung bigint;
  p_a bigint; p_b bigint; p_c bigint; p_d bigint;
BEGIN
  SELECT id INTO v_uid FROM users WHERE email = v_email;
  IF v_uid IS NULL THEN RAISE EXCEPTION 'user 없음: %  (SELECT id,email FROM users 로 확인)', v_email; END IF;
  SELECT id INTO v_other FROM users WHERE id <> v_uid ORDER BY id LIMIT 1;

  -- ── 고객 ──────────────────────────────────────────────
  INSERT INTO customers(user_id,name,phone,grade,gender,note) VALUES
    (v_uid,'김민지','010-1111-2221','vip','female','단골, 매주 화병꽂이') RETURNING id INTO c_kim;
  INSERT INTO customers(user_id,name,phone,grade,gender,note) VALUES
    (v_uid,'이서준','010-1111-2222','regular','male','프로포즈 부케 문의') RETURNING id INTO c_lee;
  INSERT INTO customers(user_id,name,phone,grade,gender,note) VALUES
    (v_uid,'박지후','010-1111-2223','regular','male',NULL) RETURNING id INTO c_park;
  INSERT INTO customers(user_id,name,phone,grade,gender,note) VALUES
    (v_uid,'최유나','010-1111-2224','new','female','인스타 보고 방문') RETURNING id INTO c_choi;
  INSERT INTO customers(user_id,name,phone,grade,gender,note) VALUES
    (v_uid,'정하늘','010-1111-2225','new','female',NULL) RETURNING id INTO c_jung;

  -- ── 매출 (3~5월, 카테고리/결제수단/채널 다양) ─────────────
  INSERT INTO sales(user_id,date,product_name,product_category,amount,payment_method,reservation_channel,customer_name,customer_phone,customer_id,note,is_unpaid,has_review) VALUES
    -- 3월
    (v_uid,'2026-03-03','기본 꽃다발','basic_bouquet',45000,'card','kakaotalk','김민지','010-1111-2221',c_kim,'생일 선물',false,true),
    (v_uid,'2026-03-07','화병꽂이','vase',60000,'transfer','phone','김민지','010-1111-2221',c_kim,NULL,false,false),
    (v_uid,'2026-03-12','미니 꽃다발','mini_bouquet',25000,'cash','road',NULL,NULL,NULL,NULL,false,false),
    (v_uid,'2026-03-14','꽃바구니','basket',80000,'naverpay','naver_booking','최유나','010-1111-2224',c_choi,'화이트데이',false,true),
    (v_uid,'2026-03-18','중형 꽃다발','medium_bouquet',55000,'card','kakaotalk',NULL,NULL,NULL,NULL,false,false),
    (v_uid,'2026-03-21','촬영부케','photo_bouquet',120000,'transfer','phone','이서준','010-1111-2222',c_lee,'스냅 촬영용',false,false),
    (v_uid,'2026-03-25','기본 꽃다발','basic_bouquet',45000,'card','road',NULL,NULL,NULL,NULL,false,false),
    (v_uid,'2026-03-28','예약','reservation',70000,'unpaid','kakaotalk','박지후','010-1111-2223',c_park,'미수 - 월말 정산',true,false),
    (v_uid,'2026-03-30','대형 꽃다발','large_bouquet',90000,'card','naver_booking',NULL,NULL,NULL,NULL,false,false),
    -- 4월
    (v_uid,'2026-04-02','미니 꽃다발','mini_bouquet',25000,'cash','road',NULL,NULL,NULL,NULL,false,false),
    (v_uid,'2026-04-05','프로포즈 꽃다발','proposal_bouquet',150000,'card','phone','이서준','010-1111-2222',c_lee,'프로포즈',false,true),
    (v_uid,'2026-04-09','화병꽂이','vase',60000,'naverpay','kakaotalk','김민지','010-1111-2221',c_kim,'정기',false,false),
    (v_uid,'2026-04-12','꽃바구니','basket',85000,'transfer','naver_booking',NULL,NULL,NULL,NULL,false,false),
    (v_uid,'2026-04-15','기본 꽃다발','basic_bouquet',45000,'card','road','최유나','010-1111-2224',c_choi,NULL,false,false),
    (v_uid,'2026-04-19','스페셜 꽃다발','special_bouquet',110000,'card','kakaotalk',NULL,NULL,NULL,NULL,false,false),
    (v_uid,'2026-04-22','단체꽃다발','group_bouquet',200000,'transfer','phone','박지후','010-1111-2223',c_park,'개업 화환 대체',false,false),
    (v_uid,'2026-04-25','미니 꽃다발','mini_bouquet',25000,'cash','road',NULL,NULL,NULL,NULL,false,false),
    (v_uid,'2026-04-27','예약','reservation',65000,'unpaid','kakaotalk',NULL,NULL,NULL,'미수',true,false),
    (v_uid,'2026-04-29','중형 꽃다발','medium_bouquet',55000,'naverpay','naver_booking',NULL,NULL,NULL,NULL,false,true),
    -- 5월 (어버이날 성수기)
    (v_uid,'2026-05-02','꽃바구니','basket',90000,'card','kakaotalk','김민지','010-1111-2221',c_kim,'어버이날',false,true),
    (v_uid,'2026-05-06','기본 꽃다발','basic_bouquet',48000,'naverpay','naver_booking',NULL,NULL,NULL,'카네이션 포함',false,false),
    (v_uid,'2026-05-08','대형 꽃다발','large_bouquet',95000,'card','phone','이서준','010-1111-2222',c_lee,'어버이날',false,true),
    (v_uid,'2026-05-08','꽃바구니','basket',88000,'card','road',NULL,NULL,NULL,'어버이날',false,false),
    (v_uid,'2026-05-08','미니 꽃다발','mini_bouquet',30000,'cash','road',NULL,NULL,NULL,'카네이션',false,false),
    (v_uid,'2026-05-11','화병꽂이','vase',60000,'transfer','kakaotalk','김민지','010-1111-2221',c_kim,NULL,false,false),
    (v_uid,'2026-05-14','스페셜 꽃다발','special_bouquet',130000,'card','naver_booking','최유나','010-1111-2224',c_choi,'기념일',false,false),
    (v_uid,'2026-05-17','기본 꽃다발','basic_bouquet',45000,'card','road',NULL,NULL,NULL,NULL,false,false),
    (v_uid,'2026-05-20','촬영부케','photo_bouquet',120000,'transfer','phone',NULL,NULL,NULL,'웨딩 스냅',false,false),
    (v_uid,'2026-05-23','예약','reservation',75000,'unpaid','kakaotalk','박지후','010-1111-2223',c_park,'미수 - 픽업예정',true,false),
    (v_uid,'2026-05-26','중형 꽃다발','medium_bouquet',55000,'naverpay','kakaotalk',NULL,NULL,NULL,NULL,false,false);

  -- ── 지출 (total_amount = unit_price*quantity) ────────────
  INSERT INTO expenses(user_id,date,item_name,category,unit_price,quantity,total_amount,payment_method,vendor,note) VALUES
    (v_uid,'2026-03-02','장미 사입','flower_purchase',30000,5,150000,'transfer','양재 화훼공판장',NULL),
    (v_uid,'2026-03-05','매장 임대료','rent',1200000,1,1200000,'transfer','건물주',NULL),
    (v_uid,'2026-03-15','전기/수도','utilities',95000,1,95000,'card',NULL,NULL),
    (v_uid,'2026-03-20','포장지/리본','supplies',45000,1,45000,'card','부자재상',NULL),
    (v_uid,'2026-04-01','튤립/작약 사입','flower_purchase',40000,6,240000,'transfer','양재 화훼공판장',NULL),
    (v_uid,'2026-04-05','매장 임대료','rent',1200000,1,1200000,'transfer','건물주',NULL),
    (v_uid,'2026-04-10','인스타 광고','advertising',100000,1,100000,'card','Meta',NULL),
    (v_uid,'2026-04-18','퀵 배송비','delivery',9000,4,36000,'cash',NULL,NULL),
    (v_uid,'2026-05-01','카네이션 대량 사입','flower_purchase',25000,20,500000,'transfer','양재 화훼공판장','어버이날 대비'),
    (v_uid,'2026-05-05','매장 임대료','rent',1200000,1,1200000,'transfer','건물주',NULL),
    (v_uid,'2026-05-12','전기/수도','utilities',110000,1,110000,'card',NULL,NULL),
    (v_uid,'2026-05-15','포장 부자재','supplies',60000,1,60000,'card','부자재상',NULL);

  -- ── 예약 (5월 말~6월 초, 리마인더 데모 1건) ──────────────
  INSERT INTO reservations(user_id,date,time,customer_name,customer_phone,title,description,status,amount,reminder_at) VALUES
    (v_uid,'2026-05-30','14:00','이서준','010-1111-2222','프로포즈 부케 픽업','레드 장미','confirmed',150000, now() + interval '10 minutes'),
    (v_uid,'2026-05-31','11:00','최유나','010-1111-2224','기념일 꽃다발','파스텔 톤','pending',60000,NULL),
    (v_uid,'2026-06-02','16:30','박지후','010-1111-2223','개업 화분','다육+관엽','confirmed',120000,NULL);

  -- ── 일정 ─────────────────────────────────────────
  INSERT INTO schedules(user_id,title,start_date,end_date,color,description) VALUES
    (v_uid,'어버이날 성수기','2026-05-06','2026-05-08','#f43f5e','카네이션 물량 확보'),
    (v_uid,'화훼공판장 정기 사입','2026-05-19','2026-05-19','#3b82f6',NULL);

  -- ── 커뮤니티 (게시글/댓글/좋아요 + 비정규화 카운트) ──────
  INSERT INTO community_posts(author_user_id,category,title,content,content_text,is_pinned,like_count,comment_count)
    VALUES (v_uid,'knowledge','수국 오래 보관하는 법',
      '{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"줄기를 사선으로 자르고 미지근한 물에..."}]}]}'::jsonb,
      '줄기를 사선으로 자르고 미지근한 물에...', true, 0, 0) RETURNING id INTO p_c;
  INSERT INTO community_posts(author_user_id,category,title,content,content_text,like_count,comment_count)
    VALUES (v_uid,'daily','오늘 어버이날 주문 폭주네요',
      '{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"카네이션이 동났어요 ㅠㅠ"}]}]}'::jsonb,
      '카네이션이 동났어요', 0, 0) RETURNING id INTO p_a;
  INSERT INTO community_posts(author_user_id,category,title,content,content_text,like_count,comment_count)
    VALUES (v_uid,'question','네이버 예약 수수료 어떻게 하세요?',
      '{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"다들 수수료 부담 어떻게..."}]}]}'::jsonb,
      '다들 수수료 부담 어떻게', 0, 0) RETURNING id INTO p_b;

  -- 댓글: p_a 에 2개(대댓글 포함)
  INSERT INTO community_comments(post_id,author_user_id,content) VALUES (p_a, v_uid, '저도요 새벽 사입 다녀왔어요');
  INSERT INTO community_comments(post_id,parent_id,author_user_id,content)
    SELECT p_a, c.id, v_uid, '몇 시에 가셨어요?' FROM community_comments c WHERE c.post_id=p_a AND c.parent_id IS NULL LIMIT 1;
  UPDATE community_posts SET comment_count=2 WHERE id=p_a;
  -- 좋아요: p_a 본인 1
  INSERT INTO community_likes(post_id,user_id) VALUES (p_a, v_uid);
  UPDATE community_posts SET like_count=1 WHERE id=p_a;

  -- 비밀글: 다른 계정이 있으면 그 사람 글로(마스킹 데모), 없으면 본인 비밀글
  INSERT INTO community_posts(author_user_id,category,title,content,content_text,is_secret)
    VALUES (COALESCE(v_other, v_uid),'review','거래처 후기 (비밀)',
      '{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"OO공판장 단가 정보입니다"}]}]}'::jsonb,
      'OO공판장 단가 정보입니다', true) RETURNING id INTO p_d;

  RAISE NOTICE '시드 완료 user_id=%, 비밀글 작성자=%', v_uid, COALESCE(v_other, v_uid);
END $$;
