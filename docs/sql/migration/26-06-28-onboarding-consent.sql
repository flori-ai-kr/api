-- 온보딩 가입 동의 기록 (2026-06-28, session2-consent)
-- 가입 시 이용약관·개인정보 수집이용(필수)·마케팅(선택) 동의를 시각·약관버전과 함께 보존한다.
-- PIPA 제15·22조 동의 입증 근거 + 정보통신망법 제50조 마케팅 수신동의 관리.
-- users 와 1:1 (PK=user_id, FK 없음 — 스키마 전역 규칙). 롤백: 26-06-28-onboarding-consent-rollback.sql
START TRANSACTION;

CREATE TABLE user_consents (
  user_id BIGINT PRIMARY KEY,
  terms_agreed BOOLEAN NOT NULL,
  privacy_agreed BOOLEAN NOT NULL,
  marketing_agreed BOOLEAN NOT NULL DEFAULT FALSE,
  policy_version TEXT NOT NULL,
  agreed_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER update_user_consents_updated_at
  BEFORE UPDATE ON user_consents FOR EACH ROW EXECUTE FUNCTION update_updated_at();

COMMIT;
