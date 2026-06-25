-- 비밀글(게시글) 기능 제거 — community_posts.is_secret 컬럼 드롭.
-- 비밀댓글(community_comments.is_secret)은 그대로 유지한다.
-- 적용 전 코드 배포(엔티티에서 isSecret 매핑 제거) 완료가 선행되어야 한다.
ALTER TABLE community_posts DROP COLUMN is_secret;
