-- 26-06-21-console-ops.sql 롤백.
START TRANSACTION;

DROP TABLE IF EXISTS withdrawal_logs;
DROP TABLE IF EXISTS support_inquiries;
DROP TABLE IF EXISTS announcements;

ALTER TABLE community_comments DROP COLUMN IF EXISTS hidden_by;
ALTER TABLE community_comments DROP COLUMN IF EXISTS hidden_at;
ALTER TABLE community_posts DROP COLUMN IF EXISTS hidden_by;
ALTER TABLE community_posts DROP COLUMN IF EXISTS hidden_at;

DROP TABLE IF EXISTS community_bans;
DROP TABLE IF EXISTS community_reports;
DROP TABLE IF EXISTS broadcasts;
DROP TABLE IF EXISTS notification_send_logs;
DROP TABLE IF EXISTS admin_audit_logs;

COMMIT;
