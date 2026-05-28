-- users.is_admin 추가 — 커뮤니티 관리자 권한(공지 작성·비밀글/댓글 열람·타인 글 삭제) 판정용.
START TRANSACTION;

ALTER TABLE users ADD COLUMN is_admin BOOLEAN NOT NULL DEFAULT FALSE;

COMMIT;
