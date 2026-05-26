-- 소셜 로그인 지원: 소셜 전용 사용자는 비밀번호/이메일이 없을 수 있다.
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;

ALTER TABLE users ADD COLUMN provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL';
ALTER TABLE users ADD COLUMN provider_id VARCHAR(255);

-- 같은 소셜 신원 = 같은 사용자 (provider_id 있는 행에만 적용)
CREATE UNIQUE INDEX uq_users_provider_identity
  ON users (provider, provider_id)
  WHERE provider_id IS NOT NULL;
