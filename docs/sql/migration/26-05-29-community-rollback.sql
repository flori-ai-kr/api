-- 롤백: 커뮤니티 테이블 제거(의존 역순).
START TRANSACTION;

DROP TABLE IF EXISTS community_likes;
DROP TABLE IF EXISTS community_comments;
DROP TABLE IF EXISTS community_posts;

COMMIT;
