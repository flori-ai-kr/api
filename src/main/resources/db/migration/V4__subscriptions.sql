-- 구독/결제 (SPEC-SERVER-014): 앱스토어 IAP + RevenueCat 웹훅으로 갱신되는 사용자별 구독 상태.
-- 서버가 SSOT — 앱은 GET /subscription 으로 상태를 동기화한다.

-- 현재 구독 상태(사용자당 1행). 멀티테넌시: user_id 격리.
CREATE TABLE subscriptions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  store VARCHAR(16) NOT NULL CHECK (store IN ('apple', 'google')),
  product_id VARCHAR(128) NOT NULL,
  entitlement VARCHAR(64) NOT NULL DEFAULT 'premium',
  status VARCHAR(16) NOT NULL DEFAULT 'none' CHECK (status IN ('active', 'in_grace', 'expired', 'none')),
  original_transaction_id VARCHAR(128),
  current_period_end TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_subscriptions_status ON subscriptions(status);

-- 웹훅 이벤트 이력(감사/디버깅용 append-only 로그). raw_event 는 원본 페이로드.
CREATE TABLE subscription_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  event_id VARCHAR(128),
  event_type VARCHAR(32) NOT NULL,
  store VARCHAR(16),
  product_id VARCHAR(128),
  raw_event JSONB,
  occurred_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_subscription_events_user_id ON subscription_events(user_id);
