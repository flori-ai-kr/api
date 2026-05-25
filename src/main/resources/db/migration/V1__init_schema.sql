-- =============================================
-- Flori Server — Flyway baseline (원본 Supabase 스키마 이식)
-- 변경점:
--   * Supabase RLS(행 수준 보안) 정책 전부 제거 → 멀티테넌시는 애플리케이션이 강제
--   * Supabase 인증 테이블 FK 제거 → 자체 users 테이블 추가, 모든 user_id가 users(id) 참조
--   * jsonb / 배열 / uuid / timestamptz 타입은 그대로 유지
--   * 복합 unique 제약 유지: (phone,user_id), (value,user_id), (name,user_id) 등
--   * 원본 schema.sql과 후속 마이그레이션의 최종 상태를 반영
--     (sales.is_unpaid, reservations.reminder_sent/pickup_completed,
--      recurring_expenses 다중값 컬럼, expenses.category CHECK 제거, calendar_events 등)
-- =============================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- updated_at 자동 갱신 트리거 함수
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- =============================================
-- 사용자 (자체 인증) — 원본 Supabase 인증 테이블 대체
-- =============================================
CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  name VARCHAR(100),
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER update_users_updated_at
  BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- =============================================
-- 고객
-- =============================================
CREATE TABLE customers (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name VARCHAR(100) NOT NULL,
  phone VARCHAR(20) NOT NULL,
  grade VARCHAR(20) DEFAULT 'new' CHECK (grade IN ('new', 'regular', 'vip', 'blacklist')),
  gender VARCHAR(10) DEFAULT NULL CHECK (gender IN ('male', 'female')),
  total_purchase_count INTEGER DEFAULT 0,
  total_purchase_amount INTEGER DEFAULT 0,
  first_purchase_date TIMESTAMPTZ,
  last_purchase_date TIMESTAMPTZ,
  note TEXT,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE (phone, user_id)
);

CREATE INDEX idx_customers_phone ON customers(phone);
CREATE INDEX idx_customers_grade ON customers(grade);
CREATE INDEX idx_customers_user_id ON customers(user_id);

CREATE TRIGGER update_customers_updated_at
  BEFORE UPDATE ON customers FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- =============================================
-- 고정비(반복 지출) 템플릿 — expenses.recurring_id가 참조하므로 먼저 생성
-- =============================================
CREATE TABLE recurring_expenses (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  item_name TEXT NOT NULL,
  category VARCHAR(30) NOT NULL,
  unit_price INTEGER NOT NULL CHECK (unit_price >= 0),
  quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity > 0),
  payment_method VARCHAR(20) NOT NULL,
  vendor TEXT,
  note TEXT,
  frequency VARCHAR(10) NOT NULL CHECK (frequency IN ('weekly', 'monthly', 'yearly')),
  interval_count INTEGER NOT NULL DEFAULT 1 CHECK (interval_count > 0),
  days_of_week INTEGER[] NOT NULL DEFAULT '{}',   -- weekly: 0(일)~6(토)
  days_of_month INTEGER[] NOT NULL DEFAULT '{}',  -- monthly: 1~31
  yearly_dates JSONB NOT NULL DEFAULT '[]'::jsonb, -- yearly: [{"m":n,"d":n}]
  start_date DATE NOT NULL,
  end_date DATE,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_recurring_expenses_user ON recurring_expenses(user_id);
CREATE INDEX idx_recurring_expenses_active ON recurring_expenses(is_active) WHERE is_active = TRUE;

CREATE TRIGGER update_recurring_expenses_updated_at
  BEFORE UPDATE ON recurring_expenses FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- =============================================
-- 예약 (sale_id FK는 sales 생성 후 ALTER로 추가 — 순환 참조 해소)
-- =============================================
CREATE TABLE reservations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  date DATE NOT NULL,
  time TIME,
  customer_name TEXT NOT NULL DEFAULT '',
  customer_phone TEXT,
  title TEXT NOT NULL DEFAULT '',
  description TEXT,
  status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'confirmed', 'completed', 'cancelled')),
  sale_id UUID,
  amount INTEGER DEFAULT 0,
  reminder_at TIMESTAMPTZ,
  reminder_sent BOOLEAN NOT NULL DEFAULT FALSE,
  pickup_completed BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_reservations_date ON reservations(date);
CREATE INDEX idx_reservations_status ON reservations(status);
CREATE INDEX idx_reservations_user_id ON reservations(user_id);

CREATE TRIGGER update_reservations_updated_at
  BEFORE UPDATE ON reservations FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- =============================================
-- 매출
-- =============================================
CREATE TABLE sales (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  date DATE NOT NULL,
  product_name VARCHAR(200) NOT NULL,
  product_category VARCHAR(100),
  amount INTEGER NOT NULL,
  payment_method VARCHAR(20) NOT NULL
    CHECK (payment_method IN ('cash', 'card', 'transfer', 'naverpay', 'kakaopay', 'unpaid')),
  card_company VARCHAR(50),
  fee INTEGER,
  expected_deposit INTEGER,
  expected_deposit_date DATE,
  deposit_status VARCHAR(20) DEFAULT 'not_applicable'
    CHECK (deposit_status IN ('pending', 'completed', 'not_applicable')),
  deposited_at TIMESTAMPTZ,
  reservation_channel VARCHAR(20) DEFAULT 'other'
    CHECK (reservation_channel IN ('phone', 'kakaotalk', 'naver_booking', 'road', 'other')),
  customer_name VARCHAR(100),
  customer_phone VARCHAR(20),
  customer_id UUID REFERENCES customers(id) ON DELETE SET NULL,
  reservation_id UUID REFERENCES reservations(id),
  note TEXT,
  is_unpaid BOOLEAN NOT NULL DEFAULT FALSE,
  has_review BOOLEAN DEFAULT FALSE,
  photos TEXT[],
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 예약 <-> 매출 양방향 FK
ALTER TABLE reservations ADD CONSTRAINT reservations_sale_id_fkey
  FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE SET NULL;

CREATE INDEX idx_sales_date ON sales(date);
CREATE INDEX idx_sales_customer_id ON sales(customer_id);
CREATE INDEX idx_sales_payment_method ON sales(payment_method);
CREATE INDEX idx_sales_deposit_status ON sales(deposit_status);
CREATE INDEX idx_sales_user_id ON sales(user_id);

CREATE TRIGGER update_sales_updated_at
  BEFORE UPDATE ON sales FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- =============================================
-- 지출 (category CHECK는 원본에서 제거됨 — 커스텀 카테고리 허용)
-- =============================================
CREATE TABLE expenses (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  date DATE NOT NULL,
  item_name VARCHAR(200) NOT NULL,
  category VARCHAR(30) NOT NULL,
  unit_price INTEGER NOT NULL,
  quantity INTEGER DEFAULT 1,
  total_amount INTEGER NOT NULL,
  payment_method VARCHAR(20) NOT NULL
    CHECK (payment_method IN ('cash', 'card', 'transfer', 'naverpay', 'kakaopay')),
  card_company VARCHAR(50),
  vendor VARCHAR(100),
  note TEXT,
  recurring_id UUID REFERENCES recurring_expenses(id) ON DELETE SET NULL,
  is_recurring_modified BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW(),
  -- 같은 템플릿·같은 날짜 중복 자동생성 방지(NULL recurring_id는 distinct 취급)
  CONSTRAINT expenses_recurring_date_unique UNIQUE (recurring_id, date)
);

CREATE INDEX idx_expenses_date ON expenses(date);
CREATE INDEX idx_expenses_category ON expenses(category);
CREATE INDEX idx_expenses_user_id ON expenses(user_id);

CREATE TRIGGER update_expenses_updated_at
  BEFORE UPDATE ON expenses FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- =============================================
-- 고정비 skip ("이것만 삭제" 시 자동생성 재발 방지)
-- =============================================
CREATE TABLE recurring_skips (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  recurring_id UUID NOT NULL REFERENCES recurring_expenses(id) ON DELETE CASCADE,
  skip_date DATE NOT NULL,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE (recurring_id, skip_date)
);

CREATE INDEX idx_recurring_skips_user ON recurring_skips(user_id);

-- =============================================
-- 카드사 설정
-- =============================================
CREATE TABLE card_company_settings (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name VARCHAR(50) NOT NULL,
  fee_rate DECIMAL(5, 2) DEFAULT 2.0,
  deposit_days INTEGER DEFAULT 3,
  is_active BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE (name, user_id)
);

CREATE INDEX idx_card_company_settings_user_id ON card_company_settings(user_id);

CREATE TRIGGER update_card_settings_updated_at
  BEFORE UPDATE ON card_company_settings FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- =============================================
-- 매출 카테고리 (value=영문 저장값, label=한글 표시값)
-- =============================================
CREATE TABLE sale_categories (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  value VARCHAR(100) NOT NULL,
  label VARCHAR(100) NOT NULL,
  color VARCHAR(7) DEFAULT '#f43f5e',
  sort_order INTEGER DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE (value, user_id)
);

CREATE INDEX idx_sale_categories_user_id ON sale_categories(user_id);

-- =============================================
-- 매출 결제방식
-- =============================================
CREATE TABLE payment_methods (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  value VARCHAR(20) NOT NULL,
  label VARCHAR(100) NOT NULL,
  color VARCHAR(7) DEFAULT '#3b82f6',
  sort_order INTEGER DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE (value, user_id)
);

CREATE INDEX idx_payment_methods_user_id ON payment_methods(user_id);

-- =============================================
-- 지출 카테고리
-- =============================================
CREATE TABLE expense_categories (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  value VARCHAR(100) NOT NULL,
  label VARCHAR(100) NOT NULL,
  color VARCHAR(7) DEFAULT '#6b7280',
  sort_order INTEGER DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE (value, user_id)
);

CREATE INDEX idx_expense_categories_user_id ON expense_categories(user_id);

-- =============================================
-- 지출 결제방식
-- =============================================
CREATE TABLE expense_payment_methods (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  value VARCHAR(20) NOT NULL,
  label VARCHAR(100) NOT NULL,
  color VARCHAR(7) DEFAULT '#3b82f6',
  sort_order INTEGER DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE (value, user_id)
);

CREATE INDEX idx_expense_payment_methods_user_id ON expense_payment_methods(user_id);

-- =============================================
-- 사진 태그
-- =============================================
CREATE TABLE photo_tags (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name VARCHAR(50) NOT NULL,
  color VARCHAR(7) DEFAULT '#6b7280',
  created_at TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE (name, user_id)
);

CREATE INDEX idx_photo_tags_user_id ON photo_tags(user_id);

-- =============================================
-- 사진 카드 (photos: [{url, originalName}] jsonb)
-- =============================================
CREATE TABLE photo_cards (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title VARCHAR(255) NOT NULL,
  description TEXT,
  tags TEXT[] DEFAULT '{}',
  photos JSONB DEFAULT '[]',
  sale_id UUID REFERENCES sales(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_photo_cards_tags ON photo_cards USING GIN(tags);
CREATE INDEX idx_photo_cards_sale_id ON photo_cards(sale_id);
CREATE INDEX idx_photo_cards_created_at ON photo_cards(created_at DESC);
CREATE INDEX idx_photo_cards_user_id ON photo_cards(user_id);

CREATE TRIGGER update_photo_cards_updated_at
  BEFORE UPDATE ON photo_cards FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- =============================================
-- 푸시 구독 (원본 Web Push 컬럼 유지 — FCM 토큰도 endpoint에 저장)
-- =============================================
CREATE TABLE push_subscriptions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  endpoint TEXT NOT NULL UNIQUE,
  p256dh TEXT,
  auth TEXT,
  user_agent TEXT,
  is_active BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_push_subscriptions_user_id ON push_subscriptions(user_id);

CREATE TRIGGER update_push_subscriptions_updated_at
  BEFORE UPDATE ON push_subscriptions FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- =============================================
-- 앱 전역 설정 (키-값, 비-테넌트)
-- =============================================
CREATE TABLE app_config (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL
);

-- =============================================
-- 캘린더 이벤트 (다중일 일정)
-- =============================================
CREATE TABLE calendar_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  color VARCHAR(7) NOT NULL DEFAULT '#f43f5e',
  description TEXT,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_calendar_events_user_id ON calendar_events(user_id);
CREATE INDEX idx_calendar_events_range ON calendar_events(start_date, end_date);

CREATE TRIGGER update_calendar_events_updated_at
  BEFORE UPDATE ON calendar_events FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- =============================================
-- 인사이트: 트렌드 기사 (공유 읽기 — 테넌트 무관)
-- =============================================
CREATE TABLE trend_articles (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  category TEXT NOT NULL CHECK (category IN ('flower', 'inspiration', 'business', 'industry')),
  title TEXT NOT NULL,
  summary TEXT NOT NULL,
  key_points JSONB NOT NULL DEFAULT '[]'::jsonb,
  source_url TEXT NOT NULL,
  source_name TEXT,
  published_at TIMESTAMPTZ,
  collected_at DATE NOT NULL,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_trend_articles_category ON trend_articles(category);
CREATE INDEX idx_trend_articles_collected_at ON trend_articles(collected_at DESC);
CREATE UNIQUE INDEX idx_trend_articles_source_url ON trend_articles(source_url);

-- =============================================
-- 인사이트: 인스타그램 계정 (공유)
-- =============================================
CREATE TABLE instagram_accounts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  username TEXT NOT NULL UNIQUE,
  display_name TEXT,
  profile_url TEXT NOT NULL,
  region TEXT NOT NULL CHECK (region IN ('domestic', 'international')),
  sort_order INT DEFAULT 0,
  active BOOLEAN DEFAULT TRUE,
  notes TEXT,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_instagram_accounts_active ON instagram_accounts(active);
CREATE INDEX idx_instagram_accounts_region ON instagram_accounts(region);

CREATE TRIGGER update_instagram_accounts_updated_at
  BEFORE UPDATE ON instagram_accounts FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- =============================================
-- 인사이트: 인스타그램 포스트 (공유)
-- =============================================
CREATE TABLE instagram_posts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id UUID NOT NULL REFERENCES instagram_accounts(id) ON DELETE CASCADE,
  shortcode TEXT NOT NULL UNIQUE,
  permalink TEXT NOT NULL,
  image_urls JSONB NOT NULL DEFAULT '[]'::jsonb,
  caption TEXT,
  like_count INT DEFAULT 0,
  posted_at TIMESTAMPTZ NOT NULL,
  scraped_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_instagram_posts_account_id ON instagram_posts(account_id);
CREATE INDEX idx_instagram_posts_posted_at ON instagram_posts(posted_at DESC);

-- =============================================
-- 유저 설정 (하단바 커스터마이즈)
-- =============================================
CREATE TABLE user_preferences (
  user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  bottom_nav_items JSONB NOT NULL DEFAULT '["calendar","sales","expenses","customers","insights"]'::jsonb,
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TRIGGER update_user_preferences_updated_at
  BEFORE UPDATE ON user_preferences FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- =============================================
-- 인사이트 스크랩/메모 (polymorphic: target_type + target_id)
-- =============================================
CREATE TABLE insight_scraps (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  target_type TEXT NOT NULL CHECK (target_type IN ('trend', 'post')),
  target_id UUID NOT NULL,
  memo TEXT,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE (user_id, target_type, target_id)
);

CREATE INDEX idx_insight_scraps_user ON insight_scraps(user_id);
CREATE INDEX idx_insight_scraps_target ON insight_scraps(target_type, target_id);
CREATE INDEX idx_insight_scraps_user_type_created
  ON insight_scraps(user_id, target_type, created_at DESC);

CREATE TRIGGER update_insight_scraps_updated_at
  BEFORE UPDATE ON insight_scraps FOR EACH ROW EXECUTE FUNCTION update_updated_at();
