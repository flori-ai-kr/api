-- 롤백 — photo_tags.color 복구(기본값 회색). 기존 색상 데이터는 복원 불가(기본값으로만 채움).

ALTER TABLE photo_tags ADD COLUMN IF NOT EXISTS color VARCHAR(7) DEFAULT '#6b7280';
