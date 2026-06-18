-- user_profiles.referral_sources 추가 — 온보딩 '참여경로'(flori를 알게 된 경로, 선택·복수) 저장. 마케팅 분석용.
-- interests/specialties와 동일한 TEXT[] 패턴. 기존 행은 DEFAULT '{}'로 채워져 NOT NULL 위반 없음.
START TRANSACTION;

ALTER TABLE user_profiles ADD COLUMN referral_sources TEXT[] NOT NULL DEFAULT '{}';

COMMIT;
