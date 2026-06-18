-- rollback: user_profiles.referral_sources 제거.
START TRANSACTION;

ALTER TABLE user_profiles DROP COLUMN referral_sources;

COMMIT;
