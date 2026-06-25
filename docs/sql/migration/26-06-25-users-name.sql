-- 사장님 실명(name)을 users로 이동. 기존 행은 닉네임으로 백필 후 NOT NULL.
ALTER TABLE users ADD COLUMN IF NOT EXISTS name TEXT;
UPDATE users SET name = nickname WHERE name IS NULL;
ALTER TABLE users ALTER COLUMN name SET NOT NULL;
