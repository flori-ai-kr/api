-- =============================================
-- Flori Server — 온보딩 애드덤(V3 forward 마이그레이션)
-- 설계 원칙:
--   * V1/V2 baseline은 건드리지 않는다(로컬 DB에 이미 적용됨) → V3로 순방향 추가만.
--   * users.onboarded: 온보딩 완료 여부 플래그(서버 SSOT). 웹은 false일 때만 /onboarding 라우팅.
--   * user_profiles: users와 1:1(PK=FK=user_id). 가게 프로필 정보 별도 보관.
--     - store_name은 users.name(계정 표시명/소셜 닉네임)과 분리한다.
--     - interests/specialties는 photo_cards.tags와 동일한 TEXT[] 컨벤션.
--   * 멀티테넌시: PK가 user_id이므로 user_profiles는 본질적으로 테넌트 격리(임의 user_id 주입 불가).
-- =============================================

-- 온보딩 완료 플래그(기존 사용자는 false로 시작)
ALTER TABLE users ADD COLUMN onboarded BOOLEAN NOT NULL DEFAULT FALSE;

-- =============================================
-- 사용자 프로필 (users와 1:1) — PK이자 FK인 user_id
-- =============================================
CREATE TABLE user_profiles (
  user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  store_name TEXT NOT NULL,
  region_sido TEXT NOT NULL,
  region_sigungu TEXT,
  owner_age_range TEXT,
  interests TEXT[] NOT NULL DEFAULT '{}',
  specialties TEXT[] NOT NULL DEFAULT '{}',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER update_user_profiles_updated_at
  BEFORE UPDATE ON user_profiles FOR EACH ROW EXECUTE FUNCTION update_updated_at();
