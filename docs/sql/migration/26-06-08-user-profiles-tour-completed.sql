-- user_profiles.tour_completed 추가 — 인앱 제품 투어 1회 노출 플래그.
-- 기본 FALSE: 기존·신규 사용자 모두 다음 /admin 첫 진입 시 투어를 1회 본다.
START TRANSACTION;

ALTER TABLE user_profiles ADD COLUMN tour_completed BOOLEAN NOT NULL DEFAULT FALSE;

COMMIT;
