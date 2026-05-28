-- =============================================
-- Flori Server — onboarded 컬럼 제거(V5 forward 마이그레이션)
-- 설계 원칙:
--   * V1~V4 baseline은 건드리지 않는다 → V5로 순방향 변경만.
--   * 소셜 전용 + registerToken 재설계 이후 User 행은 register/complete(= 온보딩 완료) 시점에만,
--     항상 onboarded=true + user_profiles 행과 함께 원자적으로 생성된다. 다른 User 생성 경로가 없다.
--   * 따라서 "User 존재" ⟺ "온보딩 완료" → users.onboarded 는 영구적으로 true인 죽은 컬럼이다.
--   * 컬럼을 제거하고 이를 참조하던 코드/응답 필드를 함께 제거한다.
-- =============================================

-- 온보딩 완료 플래그(영구 true)는 더 이상 의미가 없으므로 제거
ALTER TABLE users DROP COLUMN onboarded;
