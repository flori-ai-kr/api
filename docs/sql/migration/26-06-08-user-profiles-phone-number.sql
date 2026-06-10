-- user_profiles.phone_number 추가 — 온보딩 전화번호 필수 입력.
-- 기존 행이 있으면 임시로 '' 채운 뒤 운영자가 실제 번호로 백필한다(NOT NULL 위반 회피).
START TRANSACTION;

ALTER TABLE user_profiles ADD COLUMN phone_number TEXT NOT NULL DEFAULT '';
ALTER TABLE user_profiles ALTER COLUMN phone_number DROP DEFAULT;

COMMIT;
