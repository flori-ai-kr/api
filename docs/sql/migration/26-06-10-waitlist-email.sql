-- 사전등록(waitlist) — 식별자 phone → email 전환.
-- 개인정보 최소화: 전화번호 대신 이메일을 식별자로 수집. email·shop_name 모두 필수 유지.
-- 이메일은 정규화(trim+소문자)하여 UNIQUE로 중복 등록 방지.
--
-- ⚠️ 기존 phone 기반 등록 데이터는 이메일이 없어 이관할 수 없습니다.
--    사전출시 단계(테스트 데이터)를 가정해 기존 행을 비웁니다.
--    운영 데이터가 있다면 먼저 백업/이관한 뒤 실행하세요.
START TRANSACTION;

TRUNCATE TABLE waitlist_registrations RESTART IDENTITY;

ALTER TABLE waitlist_registrations DROP COLUMN phone;
ALTER TABLE waitlist_registrations ADD COLUMN email VARCHAR(254) NOT NULL UNIQUE;

COMMIT;
