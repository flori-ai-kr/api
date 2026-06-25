-- 비밀글(게시글) 기능 제거 — community_posts.is_secret 컬럼 드롭.
-- 비밀댓글(community_comments.is_secret)은 그대로 유지한다.
-- 적용 전 코드 배포(엔티티에서 isSecret 매핑 제거) 완료가 선행되어야 한다.
-- 참고: 이 마이그레이션 이후 users.is_admin 은 더 이상 '비밀글 열람'을 게이팅하지 않는다(비밀글 기능 자체 제거).
--       is_admin 은 공지(notice) 작성·비밀댓글 열람·타인 글 삭제 판정에만 사용된다.
ALTER TABLE community_posts DROP COLUMN is_secret;
