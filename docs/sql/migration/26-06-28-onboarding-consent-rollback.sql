-- 롤백: 온보딩 가입 동의 기록 (2026-06-28, session2-consent)
START TRANSACTION;

DROP TRIGGER IF EXISTS update_user_consents_updated_at ON user_consents;
DROP TABLE IF EXISTS user_consents;

COMMIT;
