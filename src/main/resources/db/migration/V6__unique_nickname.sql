-- =============================================
-- Flori Server — 닉네임(users.name) 유일성 강제(V6 forward 마이그레이션)
-- 설계 원칙:
--   * 닉네임은 이제 필수값이다. register/complete에서 항상 채워지므로(NotBlank) NOT NULL로 강화.
--   * 향후 커뮤니티 기능에서 닉네임이 사용자 식별 표시값이 되므로 전역 UNIQUE를 건다.
--   * 정확 일치(case-sensitive, 저장된 값 그대로) 유일성. 대소문자 무시/정규화는 도입하지 않는다.
--   * dev 단계로 운영 데이터가 없어 SET NOT NULL이 안전하다(NULL 행 부재).
-- =============================================

-- 닉네임 필수화 (register/complete에서 NotBlank로 항상 채워짐)
ALTER TABLE users ALTER COLUMN name SET NOT NULL;

-- 닉네임 전역 유일성 (정확 일치)
ALTER TABLE users ADD CONSTRAINT uq_users_name UNIQUE (name);
