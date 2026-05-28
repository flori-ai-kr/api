-- =============================================
-- Flori — 모든 외래키(FK) 제약 제거
-- =============================================
-- 참조 무결성/연쇄삭제를 DB가 아닌 "애플리케이션이 직접 관리"하는 간접참조 방식으로 전환한다.
-- FK 컬럼(user_id, sale_id, recurring_id 등)은 그대로 유지하고, 제약(constraint)만 제거한다.
--
-- [주의] 적용 후 DB의 ON DELETE CASCADE / SET NULL 동작이 사라진다.
--        아래 부모 삭제 흐름은 애플리케이션이 자식 행을 명시적으로 정리해야 한다:
--          - customer 삭제      → sales.customer_id 정리(NULL 또는 재배치)
--          - instagram_account  → instagram_posts 삭제
--          - recurring_expense  → recurring_skips 삭제 + expenses.recurring_id 정리
--          - sale 삭제          → reservations.sale_id / photo_cards.sale_id 정리
--          - user(회원탈퇴)      → 전체 자식 테이블 정리
--
-- 멱등 적용을 위해 DROP CONSTRAINT IF EXISTS 사용. 제약명은 PostgreSQL 기본 규칙(<table>_<column>_fkey).
-- 적용 후 docs/sql/all-tables-ddl.sql 에도 반영되어 있다(SSOT 동기화 완료).
-- =============================================

ALTER TABLE user_profiles           DROP CONSTRAINT IF EXISTS user_profiles_user_id_fkey;
ALTER TABLE refresh_tokens          DROP CONSTRAINT IF EXISTS refresh_tokens_user_id_fkey;
ALTER TABLE customers               DROP CONSTRAINT IF EXISTS customers_user_id_fkey;
ALTER TABLE recurring_expenses      DROP CONSTRAINT IF EXISTS recurring_expenses_user_id_fkey;
ALTER TABLE reservations            DROP CONSTRAINT IF EXISTS reservations_user_id_fkey;
ALTER TABLE reservations            DROP CONSTRAINT IF EXISTS reservations_sale_id_fkey;
ALTER TABLE sales                   DROP CONSTRAINT IF EXISTS sales_user_id_fkey;
ALTER TABLE sales                   DROP CONSTRAINT IF EXISTS sales_customer_id_fkey;
ALTER TABLE expenses                DROP CONSTRAINT IF EXISTS expenses_user_id_fkey;
ALTER TABLE expenses                DROP CONSTRAINT IF EXISTS expenses_recurring_id_fkey;
ALTER TABLE recurring_skips         DROP CONSTRAINT IF EXISTS recurring_skips_user_id_fkey;
ALTER TABLE recurring_skips         DROP CONSTRAINT IF EXISTS recurring_skips_recurring_id_fkey;
ALTER TABLE sale_categories         DROP CONSTRAINT IF EXISTS sale_categories_user_id_fkey;
ALTER TABLE payment_methods         DROP CONSTRAINT IF EXISTS payment_methods_user_id_fkey;
ALTER TABLE expense_categories      DROP CONSTRAINT IF EXISTS expense_categories_user_id_fkey;
ALTER TABLE expense_payment_methods DROP CONSTRAINT IF EXISTS expense_payment_methods_user_id_fkey;
ALTER TABLE photo_tags              DROP CONSTRAINT IF EXISTS photo_tags_user_id_fkey;
ALTER TABLE photo_cards             DROP CONSTRAINT IF EXISTS photo_cards_user_id_fkey;
ALTER TABLE photo_cards             DROP CONSTRAINT IF EXISTS photo_cards_sale_id_fkey;
ALTER TABLE push_subscriptions      DROP CONSTRAINT IF EXISTS push_subscriptions_user_id_fkey;
ALTER TABLE calendar_events         DROP CONSTRAINT IF EXISTS calendar_events_user_id_fkey;
ALTER TABLE instagram_posts         DROP CONSTRAINT IF EXISTS instagram_posts_account_id_fkey;
ALTER TABLE user_preferences        DROP CONSTRAINT IF EXISTS user_preferences_user_id_fkey;
ALTER TABLE insight_scraps          DROP CONSTRAINT IF EXISTS insight_scraps_user_id_fkey;
ALTER TABLE subscriptions           DROP CONSTRAINT IF EXISTS subscriptions_user_id_fkey;
ALTER TABLE subscription_events     DROP CONSTRAINT IF EXISTS subscription_events_user_id_fkey;
ALTER TABLE notification_log        DROP CONSTRAINT IF EXISTS notification_log_user_id_fkey;
ALTER TABLE community_posts         DROP CONSTRAINT IF EXISTS community_posts_author_user_id_fkey;
ALTER TABLE community_comments      DROP CONSTRAINT IF EXISTS community_comments_post_id_fkey;
ALTER TABLE community_comments      DROP CONSTRAINT IF EXISTS community_comments_parent_id_fkey;
ALTER TABLE community_comments      DROP CONSTRAINT IF EXISTS community_comments_author_user_id_fkey;
ALTER TABLE community_likes         DROP CONSTRAINT IF EXISTS community_likes_post_id_fkey;
ALTER TABLE community_likes         DROP CONSTRAINT IF EXISTS community_likes_user_id_fkey;
