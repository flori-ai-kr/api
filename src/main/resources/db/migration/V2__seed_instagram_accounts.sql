-- 인사이트 팔로우 계정 시드 (공유 참조 데이터, 테넌트 무관)
-- 원본: flori-ai/web/supabase/migrations/2026-04-17-insights.sql
INSERT INTO instagram_accounts (username, display_name, profile_url, region, sort_order) VALUES
  ('heartmadebykigpcn',   'Heart Made by KIG',    'https://www.instagram.com/heartmadebykigpcn',   'international', 10),
  ('futurejenn',          'Future Jenn',          'https://www.instagram.com/futurejenn',          'international', 20),
  ('ffoliar',             'ffoliar',              'https://www.instagram.com/ffoliar',             'international', 30),
  ('yourlondonflorist',   'Your London Florist',  'https://www.instagram.com/yourlondonflorist',   'international', 40),
  ('nafleur.j',           'Nafleur J',            'https://www.instagram.com/nafleur.j',           'international', 50),
  ('farishtaflowers',     'Farishta Flowers',     'https://www.instagram.com/farishtaflowers',     'international', 60),
  ('dada.island',         'Dada Island',          'https://www.instagram.com/dada.island',         'international', 70),
  ('sohee_elletravaille', 'Sohee Elle Travaille', 'https://www.instagram.com/sohee_elletravaille', 'international', 80),
  ('blxxm__',             'Blxxm',                'https://www.instagram.com/blxxm__',             'international', 90),
  ('hamishpowell',        'Hamish Powell',        'https://www.instagram.com/hamishpowell',        'international', 100),
  ('ohhoneyflorals',      'Oh Honey Florals',     'https://www.instagram.com/ohhoneyflorals',      'international', 110),
  ('isadiafloral',        'Isadia Floral',        'https://www.instagram.com/isadiafloral',        'international', 120),
  ('edenflorals.studio',  'Eden Florals Studio',  'https://www.instagram.com/edenflorals.studio',  'international', 130),
  ('madridflowerschool',  'Madrid Flower School', 'https://www.instagram.com/madridflowerschool',  'international', 140),
  ('duodesfleurs_kr',     'Duo des Fleurs',       'https://www.instagram.com/duodesfleurs_kr',     'domestic',     200)
ON CONFLICT (username) DO NOTHING;
