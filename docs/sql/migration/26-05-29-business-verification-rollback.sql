-- 롤백: business_verifications 제거.
START TRANSACTION;
DROP TABLE IF EXISTS business_verifications;
COMMIT;
