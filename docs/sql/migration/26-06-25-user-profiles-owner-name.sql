-- 사장님 실명(owner_name) 추가. 기존 유저는 NULL 허용(백필 없음).
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS owner_name TEXT;
