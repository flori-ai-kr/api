-- =============================================
-- Flori Server — 소셜 전용 인증 전환(V4 forward 마이그레이션)
-- 설계 원칙:
--   * V1~V3 baseline은 건드리지 않는다(로컬 DB에 이미 적용됨) → V4로 순방향 변경만.
--   * 이메일/비밀번호 가입 폐지: password_hash 컬럼 제거(더 이상 사용 안 함).
--   * User는 온보딩 완료(register/complete) 시점에만 생성되며 email이 항상 채워진다 → email NOT NULL.
--     (dev DB는 비어 있어 안전. 기존 UNIQUE(email)과 부분 UNIQUE(provider, provider_id)는 유지.)
--   * onboarded 컬럼과 user_profiles 테이블은 V3에서 이미 생성됨 — 여기서는 손대지 않는다.
-- =============================================

-- 비밀번호 컬럼 제거(이메일/비밀번호 가입 폐지)
ALTER TABLE users DROP COLUMN password_hash;

-- 이메일은 온보딩에서 항상 채워지므로 NOT NULL로 강화(UNIQUE 제약은 V1 그대로 유지)
ALTER TABLE users ALTER COLUMN email SET NOT NULL;
