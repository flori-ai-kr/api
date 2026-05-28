-- =============================================
-- ROLLBACK: 모든 외래키(FK) 제약 복원
-- =============================================
-- 26-05-29-drop-foreign-keys.sql 의 역연산. 원래의 ON DELETE 동작(CASCADE / SET NULL)까지 복원한다.
-- [전제] 복원 시점에 고아 데이터가 없어야 한다(FK 추가가 기존 위반 행 때문에 실패할 수 있음).
-- =============================================

ALTER TABLE user_profiles           ADD CONSTRAINT user_profiles_user_id_fkey           FOREIGN KEY (user_id)        REFERENCES users(id)               ON DELETE CASCADE;
ALTER TABLE refresh_tokens          ADD CONSTRAINT refresh_tokens_user_id_fkey          FOREIGN KEY (user_id)        REFERENCES users(id)               ON DELETE CASCADE;
ALTER TABLE customers               ADD CONSTRAINT customers_user_id_fkey               FOREIGN KEY (user_id)        REFERENCES users(id)               ON DELETE CASCADE;
ALTER TABLE recurring_expenses      ADD CONSTRAINT recurring_expenses_user_id_fkey      FOREIGN KEY (user_id)        REFERENCES users(id)               ON DELETE CASCADE;
ALTER TABLE reservations            ADD CONSTRAINT reservations_user_id_fkey            FOREIGN KEY (user_id)        REFERENCES users(id)               ON DELETE CASCADE;
ALTER TABLE reservations            ADD CONSTRAINT reservations_sale_id_fkey            FOREIGN KEY (sale_id)        REFERENCES sales(id)               ON DELETE SET NULL;
ALTER TABLE sales                   ADD CONSTRAINT sales_user_id_fkey                   FOREIGN KEY (user_id)        REFERENCES users(id)               ON DELETE CASCADE;
ALTER TABLE sales                   ADD CONSTRAINT sales_customer_id_fkey               FOREIGN KEY (customer_id)    REFERENCES customers(id)           ON DELETE SET NULL;
ALTER TABLE expenses                ADD CONSTRAINT expenses_user_id_fkey                FOREIGN KEY (user_id)        REFERENCES users(id)               ON DELETE CASCADE;
ALTER TABLE expenses                ADD CONSTRAINT expenses_recurring_id_fkey           FOREIGN KEY (recurring_id)   REFERENCES recurring_expenses(id)  ON DELETE SET NULL;
ALTER TABLE recurring_skips         ADD CONSTRAINT recurring_skips_user_id_fkey         FOREIGN KEY (user_id)        REFERENCES users(id)               ON DELETE CASCADE;
ALTER TABLE recurring_skips         ADD CONSTRAINT recurring_skips_recurring_id_fkey    FOREIGN KEY (recurring_id)   REFERENCES recurring_expenses(id)  ON DELETE CASCADE;
ALTER TABLE sale_categories         ADD CONSTRAINT sale_categories_user_id_fkey         FOREIGN KEY (user_id)        REFERENCES users(id)               ON DELETE CASCADE;
ALTER TABLE payment_methods         ADD CONSTRAINT payment_methods_user_id_fkey         FOREIGN KEY (user_id)        REFERENCES users(id)               ON DELETE CASCADE;
ALTER TABLE expense_categories      ADD CONSTRAINT expense_categories_user_id_fkey      FOREIGN KEY (user_id)        REFERENCES users(id)               ON DELETE CASCADE;
ALTER TABLE expense_payment_methods ADD CONSTRAINT expense_payment_methods_user_id_fkey FOREIGN KEY (user_id)        REFERENCES users(id)               ON DELETE CASCADE;
ALTER TABLE photo_tags              ADD CONSTRAINT photo_tags_user_id_fkey              FOREIGN KEY (user_id)        REFERENCES users(id)               ON DELETE CASCADE;
ALTER TABLE photo_cards             ADD CONSTRAINT photo_cards_user_id_fkey             FOREIGN KEY (user_id)        REFERENCES users(id)               ON DELETE CASCADE;
ALTER TABLE photo_cards             ADD CONSTRAINT photo_cards_sale_id_fkey             FOREIGN KEY (sale_id)        REFERENCES sales(id)               ON DELETE SET NULL;
ALTER TABLE push_subscriptions      ADD CONSTRAINT push_subscriptions_user_id_fkey      FOREIGN KEY (user_id)        REFERENCES users(id)               ON DELETE CASCADE;
ALTER TABLE calendar_events         ADD CONSTRAINT calendar_events_user_id_fkey         FOREIGN KEY (user_id)        REFERENCES users(id)               ON DELETE CASCADE;
ALTER TABLE instagram_posts         ADD CONSTRAINT instagram_posts_account_id_fkey      FOREIGN KEY (account_id)     REFERENCES instagram_accounts(id)  ON DELETE CASCADE;
ALTER TABLE user_preferences        ADD CONSTRAINT user_preferences_user_id_fkey        FOREIGN KEY (user_id)        REFERENCES users(id)               ON DELETE CASCADE;
ALTER TABLE insight_scraps          ADD CONSTRAINT insight_scraps_user_id_fkey          FOREIGN KEY (user_id)        REFERENCES users(id)               ON DELETE CASCADE;
ALTER TABLE subscriptions           ADD CONSTRAINT subscriptions_user_id_fkey           FOREIGN KEY (user_id)        REFERENCES users(id)               ON DELETE CASCADE;
ALTER TABLE subscription_events     ADD CONSTRAINT subscription_events_user_id_fkey     FOREIGN KEY (user_id)        REFERENCES users(id)               ON DELETE CASCADE;
ALTER TABLE notification_log        ADD CONSTRAINT notification_log_user_id_fkey        FOREIGN KEY (user_id)        REFERENCES users(id)               ON DELETE CASCADE;
ALTER TABLE community_posts         ADD CONSTRAINT community_posts_author_user_id_fkey  FOREIGN KEY (author_user_id) REFERENCES users(id)               ON DELETE CASCADE;
ALTER TABLE community_comments      ADD CONSTRAINT community_comments_post_id_fkey      FOREIGN KEY (post_id)        REFERENCES community_posts(id)     ON DELETE CASCADE;
ALTER TABLE community_comments      ADD CONSTRAINT community_comments_parent_id_fkey    FOREIGN KEY (parent_id)      REFERENCES community_comments(id)  ON DELETE CASCADE;
ALTER TABLE community_comments      ADD CONSTRAINT community_comments_author_user_id_fkey FOREIGN KEY (author_user_id) REFERENCES users(id)             ON DELETE CASCADE;
ALTER TABLE community_likes         ADD CONSTRAINT community_likes_post_id_fkey         FOREIGN KEY (post_id)        REFERENCES community_posts(id)     ON DELETE CASCADE;
ALTER TABLE community_likes         ADD CONSTRAINT community_likes_user_id_fkey         FOREIGN KEY (user_id)        REFERENCES users(id)               ON DELETE CASCADE;
