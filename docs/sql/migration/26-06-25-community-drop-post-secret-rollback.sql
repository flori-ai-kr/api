-- 롤백 — community_posts.is_secret 컬럼 복원(기본 FALSE). 기존 비밀글 상태는 복원되지 않는다.
ALTER TABLE community_posts ADD COLUMN is_secret BOOLEAN NOT NULL DEFAULT FALSE;
