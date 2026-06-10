-- 사진 태그 색상 제거 — 태그는 #해시태그 텍스트로만 표시(색상 개념 폐기).
-- 안전: 컬럼 삭제는 비가역. 적용 전 백업 권장.

ALTER TABLE photo_tags DROP COLUMN IF EXISTS color;
