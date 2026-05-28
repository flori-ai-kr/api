-- 롤백: users.is_admin 제거.
START TRANSACTION;

ALTER TABLE users DROP COLUMN is_admin;

COMMIT;
