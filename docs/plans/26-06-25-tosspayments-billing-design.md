# 토스페이먼츠 자동결제(빌링) + 구독 + 쿠폰 시스템 설계

- 작성일: 2026-06-25
- 대상 레포: `api`(Spring Boot/Kotlin) + `web`(Next.js) — 워크트리 `session4-billing`
- 상태: 설계 확정(브레인스토밍 합의 완료), 구현 전
- 모바일: 이번 스코프 제외

---

## 1. 개요 / 목표

flori 유료 구독을 위해 **토스페이먼츠 자동결제(빌링)** 를 연동한다. 카드 1회 등록(빌링키 발급) 후 매 주기 자동 결제하는 정기구독 모델이며, **14일 무료체험 → 자동 유료전환** 구조다. 슈퍼어드민이 발행/관리하는 **쿠폰(무료 일수)** 시스템을 포함한다.

토스페이먼츠는 자동결제 **심사 중**이므로, 라이브 전환 전까지 **테스트 키 + 가상결제**로 전 플로우를 구현·검증한다. (자동결제는 일반결제와 별도 심사 — 심사 통과 후 라이브 키로 전환)

### 비목표 (v1 스코프 밖 → §12)
- 친구초대 양면 보상(초대자 보상), 유저별 고유 레퍼럴 코드 자동생성
- 월↔연 플랜 변경(프로레이션)
- 관리자 환불 승인 UI (환불은 토스 대시보드 수동, 웹훅으로 가시성만 확보)
- 다티어 플랜, 멀티 인스턴스 분산 락

---

## 2. 확정 사양

| 항목 | 결정 |
|------|------|
| 제품 | 토스 자동결제(빌링), **카드 결제창만** |
| 체험 | 카드 선등록형 **14일 무료체험** → 종료일 자동 첫 결제. **신원 1개당 1회** |
| 플랜 | **월 14,900원** / **연 154,800원**(월환산 12,900, 약 13%↓) 일시불 |
| 실패 대응(dunning) | 결제 실패 → `IN_GRACE` **3일** 유지·매일 재시도 → 실패 지속 시 `EXPIRED`(무료강등) |
| 쿠폰 | 무료 "일수" 크레딧. 콘솔에서 발행·관리. 코드 등록 + `signup?code=` 링크. 사용 시 결제일을 N일 미룸 |
| 해지 | 즉시중단 아님 → `cancel_at_period_end` 플래그, 기간 종료 시 `EXPIRED` |
| 알림 | 결제 예정 D-3 푸시 + 모든 결제 이벤트 로그 + 디스코드 |
| 어뷰징 방어 | 탈퇴→재가입 체험·쿠폰 파밍 차단 (신원 원장) |
| 스코프 | web + api (모바일 제외) |

---

## 3. 핵심 개념

### 3-1. 키 4종

| 키 | 발급 주체 | 정체 | 저장 |
|------|------|------|------|
| `clientKey`/`secretKey` | 토스 → 우리 상점 | API 키(앞=프론트, 뒤=서버). 유저별 아님 | env |
| `customerKey` | **우리 생성** | 손님 식별자(랜덤 UUID, 1인 1개). requestBillingAuth/승인에 사용 | DB |
| `authKey` | 토스(임시) | 카드등록창 완료 시 받는 1회용 증표 | 미저장(즉시 교환) |
| `billingKey` | 토스(영구) | authKey 교환으로 발급. 유저 카드를 대리하는 암호화 토큰 | **DB(암호화)** |

> 실제 카드정보는 토스가 보관. 우리는 billingKey만 들고, 매 결제 시 토스에 제시한다.

### 3-2. 구독 상태머신

상태: `TRIALING` / `ACTIVE` / `IN_GRACE` / `EXPIRED` (구독 없음 = row 없음 = `NONE`). 기존 콘솔 어휘(active/inGrace/expired/none)에 `trialing`만 추가.

```
NONE ──subscribe(카드등록)──▶ TRIALING ──14일 후 첫결제 성공──▶ ACTIVE
                                  │                              │
                            첫결제 실패                      갱신 실패
                                  ▼                              ▼
                              IN_GRACE ◀───────────────────── IN_GRACE
                                  │ 3일간 매일 재시도 (기능 유지)
                          ┌───────┴────────┐
                       성공                 3일 경과
                          ▼                    ▼
                       ACTIVE              EXPIRED (무료강등/페이월)

해지: cancel_at_period_end=true → 다음 결제일에 결제 안 함 → EXPIRED
```

### 3-3. 결제 주기(billing cycle)

"결제가 어느 이용기간(period_start)에 속하는지"를 가리키는 꼬리표. 재시도가 며칠에 성공하든 같은 주기로 묶인다(= 9월 월세는 며칠에 내든 9월분). "한 주기당 성공 결제 1건" 규칙의 키.

### 3-4. 무료 일수(쿠폰) 처리 모델

쿠폰 = "결제일을 N일 미루는" 크레딧. 체험/유료 불문 **`next_billing_at += days`** 한 줄. 금액 크레딧 아님. 여러 개 누적(가산). 트라이얼 14일 + 쿠폰 60일 = 74일 무료(가산).

---

## 4. 아키텍처 개요

### api — 신규 `billing` 도메인 (기존 도메인 패키지 컨벤션 따름)
- `BillingClient`(RestClient) — 토스 API 캡슐화
- 엔티티: `BillingKey`, `Subscription`, `PaymentHistory`, `Coupon`, `CouponRedemption`, `SubscriptionEligibility`
- 서비스: `SubscriptionService`, `CouponService`, `PaymentService`, `RecurringBillingScheduler`, `BillingEventNotifier`
- 컨트롤러: `BillingController`(점주), `AdminCouponController`(콘솔), `TossWebhookController`
- 설정: `TossPaymentsProperties`, `BillingErrorCode`(E-BILLING-NNN)
- 마이그레이션: `docs/sql/migration/26-06-25-billing.sql` + `docs/sql/all-tables-ddl.sql` 갱신 (Flyway 아님, 수동)

### web — `/admin/billing`(점주) + `/console/coupons`(콘솔)
- Server Action → `apiFetch` → BFF (기존 패턴)
- `@tosspayments/tosspayments-sdk`, `NEXT_PUBLIC_TOSS_CLIENT_KEY`
- `<SubscriptionGate>` (기존 `BusinessVerificationGate` 패턴)

### 기존 코드 접점
- `User`(BIGINT PK) 변경 없음, FK만 연결
- 기존 `/admin/subscriptions` 콘솔·`AdminOverview`의 `subscriptionStatus`를 신규 `subscription.status`에 연결
- `RecurringExpenseGenerator`의 `@Scheduled`+멱등(UNIQUE/ON CONFLICT) 패턴, RestClient 패턴, `TenantContext.getUserId()`, `DiscordErrorReporter` 패턴 재사용

---

## 5. 데이터 모델

PostgreSQL, snake_case, `BIGINT` PK, `TIMESTAMPTZ`. `ddl-auto: validate` → 엔티티↔스키마 정확 일치. 아래는 설계 스케치(실제 DDL은 구현 시 마이그레이션 파일에 확정).

### 5-1. `billing_key` — 토스 카드 토큰 보관함
- `id` PK, `user_id`(**UNIQUE**, FK users), `customer_key`, `billing_key`(**암호화**), `card_company`, `card_number_masked`, `card_type`, `status`(ACTIVE/DELETED), `created_at`, `updated_at`

### 5-2. `subscription` — 유저 구독 상태판
- `id` PK, `user_id`(**UNIQUE**, FK), `plan`(MONTHLY/YEARLY), `status`(TRIALING/ACTIVE/IN_GRACE/EXPIRED), `billing_key_id`(FK), `current_period_start`, `current_period_end`, **`next_billing_at`**, `cancel_at_period_end`(default false), `grace_until`(nullable), `retry_count`(default 0), `amount`(가격 스냅샷), `created_at`, `updated_at`

### 5-3. `payment_history` — 결제 시도 장부(append-only)
- `id` PK, `user_id`, `subscription_id`(FK), `order_id`(우리생성, 시도마다 유니크), `billing_cycle`(이 결제가 속한 주기=period_start), `amount`, `status`(PAID/FAILED/CANCELED), `toss_payment_key`(nullable), `failure_code`, `failure_message`, `approved_at`, `created_at`
- **부분 유니크**: `UNIQUE(subscription_id, billing_cycle) WHERE status='PAID'` → 한 주기 성공결제 1건 강제(이중청구 방지)

### 5-4. `coupon` — 쿠폰 정의(콘솔 발행)
- `id` PK, `code`(**UNIQUE**), `grant_type`(FREE_DAYS), `days`, `valid_from`, `valid_until`, `max_redemptions`(nullable), `per_user_limit`(default 1), `redeemed_count`(default 0), `status`(ACTIVE/DISABLED), `source`(PROMO/REFERRAL/EVENT/MANUAL), `memo`, `created_by`(admin user id), `created_at`, `updated_at`
- 코드 자동생성: 12자, 헷갈림 제외 32종 문자(Crockford Base32), `XXXX-XXXX-XXXX`. 충돌 시 재생성. 커스텀 직접입력 허용(공개 캠페인용)

### 5-5. `coupon_redemption` — 사용 기록
- `id` PK, `coupon_id`(FK), `user_id`, `granted_days`, `redeemed_at`, `subscription_id`(nullable=가입 전 pending)
- **UNIQUE(coupon_id, user_id)** → 1인 1회

### 5-6. `subscription_eligibility` — 어뷰징 방어 신원 원장(탈퇴해도 유지)
- `id` PK, `identity_hash`(**UNIQUE**, provider+providerId 해시), `trial_used_at`(nullable), `created_at`, `updated_at`
- (필요 시) 어뷰징 민감 쿠폰 사용 이력도 신원 기준 보강

---

## 6. API 계약

### 6-A. 점주앱 → 우리 서버 (`requireAuth`)
| 엔드포인트 | 역할 | body/응답 |
|------|------|------|
| `GET /billing/prepare` | 카드창 직전 준비 | → `{ customerKey }`(유저별 고정, 없으면 생성) |
| `POST /billing/subscribe` | 카드등록 완료 후 구독 시작 | `{ plan, authKey, customerKey }` → 빌링키 발급+저장+구독 생성(TRIALING, 어뷰징 체크) → `{ subscription }` |
| `GET /billing/me` | 내 구독/카드/결제내역 | → `{ subscription, card, recentPayments }` |
| `POST /billing/cancel` | 해지 예약 | → `cancel_at_period_end=true` |
| `POST /billing/resume` | 해지 취소(만료 전) | → `cancel_at_period_end=false` |
| `POST /billing/card` | 카드 교체 | `{ authKey, customerKey }` |
| `POST /coupons/redeem` | 쿠폰 등록(**레이트리밋**) | `{ code }` → 검증·적용 → `{ grantedDays, nextBillingAt }` |

### 6-B. 콘솔 → 우리 서버 (`requireAdmin`)
| 엔드포인트 | 역할 |
|------|------|
| `POST /admin/coupons` | 발행 (`{ days, code?(없으면 자동생성), validFrom, validUntil, maxRedemptions, perUserLimit, source, memo }`) |
| `GET /admin/coupons` | 목록 + 상태 |
| `GET /admin/coupons/{id}` | 상세 + 사용현황(redemptions) |
| `POST /admin/coupons/{id}/disable` | 폐기 |
| `GET /admin/subscriptions` | (기존) 신규 테이블 연결 |

### 6-C. 우리 서버 → 토스 (`BillingClient`, secretKey Basic 인증)
| 호출 | 토스 엔드포인트 | 비고 |
|------|------|------|
| 빌링키 발급 | `POST /v1/billing/authorizations/issue` `{authKey, customerKey}` | subscribe 안에서 |
| 자동결제 승인 | `POST /v1/billing/{billingKey}` `{customerKey, amount, orderId, orderName}` | 스케줄러, **`Idempotency-Key` 헤더** |
| (환불은 대시보드 수동) | — | 웹훅으로 수신만 |

- 인증: `Authorization: Basic base64(secretKey + ":")` (콜론 필수)

### 6-D. 점주 브라우저 → 토스 (SDK, clientKey)
```js
const tp = await loadTossPayments(NEXT_PUBLIC_TOSS_CLIENT_KEY)
const payment = tp.payment({ customerKey })          // /billing/prepare에서 받음
await payment.requestBillingAuth({
  method: 'CARD',
  successUrl: '.../admin/billing/success',           // authKey+customerKey 복귀
  failUrl:    '.../admin/billing/fail',
})
```

### 6-E. 구독 시작 1바퀴
```
1. checkout → GET /billing/prepare → customerKey
2. requestBillingAuth(customerKey) → 토스 카드창 → 카드입력
3. successUrl?authKey=&customerKey= 복귀
4. success 라우트 → Server Action → POST /billing/subscribe
5. 서버: 토스 빌링키 발급 → billing_key 저장 → subscription(TRIALING, next=+14d) 생성 → pending 쿠폰 적용
6. "14일 무료체험 시작! 다음 결제 MM/DD" 표시
```

---

## 7. 결제 엔진

### 7-1. 메인 스케줄러 (`@Scheduled` KST 04:00)
```
next_billing_at <= 오늘 인 구독(TRIALING/ACTIVE/IN_GRACE) 처리:
  if cancel_at_period_end: → EXPIRED; notify·discord; continue
  if already_paid(sub, cycle): continue                 # 멱등
  result = tossBillingApprove(billingKey, amount, orderId, Idempotency-Key)
  성공 → PaymentHistory(PAID); period 전진(next += 1개월/1년); ACTIVE; retry_count=0; grace_until=null; discord
  실패 → PaymentHistory(FAILED, code)
         if status != IN_GRACE: → IN_GRACE; grace_until = 오늘+3일
         retry_count++
         if 오늘 >= grace_until: → EXPIRED; notify·discord(연체 해지)
         else: notify(카드 확인)·discord(재시도 예정)  # next_billing_at 고정 → 내일 재시도
```

### 7-2. 사전 알림 스케줄러 (D-3)
`next_billing_at`가 3일 뒤인 구독 → "결제 예정" 푸시 1회(중복 가드).

### 7-3. 토스 웹훅 (`POST /webhooks/toss`, 공개+서명검증)
대시보드 수동 환불/취소 등 상태변경 수신 → `payment_history` 동기화(CANCELED) → 필요 시 구독 보정 → 디스코드 경보. (환불 승인 UI 없이 가시성 확보)

### 7-4. 결제 이벤트 → 로그 + 디스코드 (`BillingEventNotifier`, `DISCORD_BILLING_WEBHOOK_URL`)
| 이벤트 | 로그 | 디스코드 | 유저푸시 |
|------|----|------|------|
| 구독시작(체험) | ✓ | ✓ | ✓ |
| 결제 성공/갱신 | ✓ | ✓ | – |
| 결제 실패 | ✓ | ✓ | ✓ |
| 연체→만료 | ✓ | ✓ | ✓ |
| 해지 예약/만료 | ✓ | ✓ | – |
| 쿠폰 사용 | ✓ | ✓ | – |
| 환불(웹훅) | ✓ | ✓ | – |

### 7-5. 재가입 어뷰징 방어 (subscribe 시점)
```
identity = hash(provider + providerId)   # user_id 아님 → 재가입해도 동일
if eligibility[identity].trial_used: 체험 없이 시작(next_billing_at=지금, 즉시결제)
else: 체험 14일 + eligibility 기록
쿠폰: 어뷰징 민감 건은 identity 기준 중복 차단
```

---

## 8. 프론트엔드

### 8-1. 점주앱 (`/admin/*`)
- 게이트 순서: 로그인 → 사업자인증(APPROVED) → **구독 게이트**
  - `NONE/EXPIRED` → 페이월(플랜 선택+구독 CTA), 진입 차단
  - `TRIALING/ACTIVE` → 정상
  - `IN_GRACE` → 진입 허용 + 상단 빨강 배너("결제 실패, 카드 확인")
- 라우트: `/admin/billing`(관리), `/admin/billing/checkout`(플랜선택→requestBillingAuth), `/admin/billing/success`·`/fail`(콜백)
- 쿠폰 등록 입력 + `signup?code=` 자동 redeem

### 8-2. 콘솔 (`/console/*`)
- `/console/coupons` 목록(코드·일수·상태·사용량·기간) + [발행 모달]
- `/console/coupons/[id]` 상세 + 사용현황 + [폐기]
- `/console/subscriptions`(기존) 신규 테이블 연결

### 8-3. 기술
- `@tosspayments/tosspayments-sdk`(React 19 → `--legacy-peer-deps` 가능성)
- `NEXT_PUBLIC_TOSS_CLIENT_KEY` + `validateEnv()` 등록
- `src/lib/actions/billing.ts` + `admin-coupons.ts`
- shadcn/ui + Dusty Rose 팔레트, 상태 Badge(trial=info/grace=warning/expired=danger)

### 8-4. 출시(6/29) 마이그레이션
기존 활성 유저 전원에게 **14일 체험 자동 부여**(첫 신원이므로) 후 게이트 ON. 갑작스러운 차단 방지.

---

## 9. 에러 처리 · 테스트

### 9-1. 에러 코드 `E-BILLING-NNN`
| 코드 | 상황 |
|------|------|
| 001 | 빌링키 발급 실패(authKey 만료/무효) |
| 002 | 자동결제 승인 거절(카드사) |
| 003 | 구독 상태 부적합 |
| 101~104 | 쿠폰: 무효/기간아님/소진/폐기 |
| 201 | 웹훅 서명 검증 실패 |

기존 `GlobalExceptionHandler` + `ErrorResponse(code,message)`. 5xx 기존 Discord 자동리포트, 정상 이벤트는 `BillingEventNotifier`.

### 9-2. 토스 실패 분류
| 유형 | 처리 |
|------|------|
| 카드사 거절(한도·정지·잔액) | 재시도 → IN_GRACE 3일 |
| 입력 오류(잘못된 amount 등) | 즉시 실패, 재시도 X, 경보 |
| 토스/네트워크 일시오류 | 이번 회차 skip → 다음날 재시도 |

### 9-3. 안전장치
- 멱등성: Idempotency-Key + `(subscription_id, billing_cycle) WHERE PAID` 부분유니크 + already_paid 체크
- 동시성: 단일 인스턴스 가정(기존 스케줄러 동일). 멀티 대비는 `SELECT … FOR UPDATE SKIP LOCKED`/ShedLock(v1 제외)
- 시간 주입: `Clock` 빈 주입(테스트 고정 시각)
- 비밀: secretKey·암호화키 prod 부팅 검증, billingKey 컬럼 암호화

### 9-4. 테스트 (JUnit5 + Mockito + Zonky PG, JaCoCo 80%)
| 대상 | 시나리오 |
|------|------|
| `BillingClient` | 토스 모킹: 성공/거절/타임아웃 |
| `SubscriptionService` | subscribe(체험·어뷰징 차단)·cancel·resume |
| 쿠폰 | 날짜밀기·검증4종·폐기차단·pending→구독시 적용 |
| 스케줄러 | trial→첫결제/갱신/실패→GRACE→재시도→ACTIVE/유예소진→EXPIRED/cancel→EXPIRED/멱등(2회=1결제) |
| 웹훅 | 서명검증+환불→history 동기화+디코 |
| 프론트 | Vitest(액션)+Playwright(구독 플로우 부분 모킹) |

### 9-5. 수동 통합 검증(테스트키)
체험상점 테스트키 → 카드창(테스트 BIN) → 본인인증 `000000` → 빌링키 발급 → subscribe → 스케줄러 수동 트리거 → 가상 승인 → 상태전이 확인.

---

## 10. 환경변수 / 설정
| 위치 | 키 | 비고 |
|------|------|------|
| api | `TOSS_SECRET_KEY` | 서버, prod 필수 |
| api | `TOSS_BILLING_BASE_URL` | 기본 `https://api.tosspayments.com` |
| api | `BILLING_ENCRYPTION_KEY` | billingKey 암호화 |
| api | `DISCORD_BILLING_WEBHOOK_URL` | 이벤트 알림 |
| api | `TOSS_WEBHOOK_SECRET` | 웹훅 서명검증 |
| web | `NEXT_PUBLIC_TOSS_CLIENT_KEY` | 브라우저 |

---

## 11. 함정 체크리스트 (PG 연동 경험담 반영)
- [x] 자동결제는 일반결제와 별도 심사 → 테스트키로 먼저 완성
- [x] "정책 > 코딩": 상태전이·예외정책이 본체 (본 설계의 핵심)
- [x] 재가입 어뷰징(체험·쿠폰 파밍) → 신원 원장 방어
- [x] 결제 사전 알림(D-3)
- [x] 환불 가시성(웹훅+디코) — 승인 UI 없이
- [x] 멱등성(이중청구) — 부분유니크 + Idempotency-Key
- [N/A] 서버리스 함수 타임아웃 (EC2라 무관)

---

## 12. 스코프 밖 (v1.1+)
- 친구초대 양면 보상(초대자 보상), 유저별 고유 레퍼럴 코드
- 월↔연 플랜 변경(프로레이션) — v1은 해지 후 재구독
- 관리자 환불 승인 UI
- 다티어 플랜, 크레딧/사용량 기반 과금
- 멀티 인스턴스 분산 락

---

## 13. 구현 시 확인 사항(검증 필요)
- 토스 빌링 API 엔드포인트/필드 최신본을 공식문서로 재확인 (`/v1/billing/authorizations/issue`, `/v1/billing/{billingKey}`)
- 토스 웹훅 서명 검증 방식(공식문서)
- 기존 코드의 시간(now()) 사용 방식 → `Clock` 주입 적용점
- `@tosspayments/tosspayments-sdk` React 19 호환(`--legacy-peer-deps` 여부)
- billingKey 컬럼 암호화 방식: JPA `AttributeConverter` vs 앱레벨 AES
- 기존 `subscription_status` 노출 코드와 신규 `subscription.status` 매핑 정합
```
