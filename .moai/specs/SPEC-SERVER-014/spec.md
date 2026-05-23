# SPEC-SERVER-014 — 결제/구독 백엔드 (B2B SaaS · IAP)

## 목표
앱스토어 인앱결제(IAP) 기반 B2B SaaS 구독을 백엔드에서 관리한다. RevenueCat을 결제 중개로 사용하고, 백엔드는 RevenueCat 웹훅을 수신해 사용자별 구독 상태(엔티틀먼트)를 저장·제공하며, 프리미엄 기능 접근을 게이팅한다.

## 범위 [코드 구현만 — HARD]
- 실제 RevenueCat 계정/스토어 상품 연결, 실제 키 발급, 배포, `terraform/gradle` 외부 연동 apply 는 **하지 않는다**.
- 시크릿/키는 환경변수 placeholder(`${REVENUECAT_WEBHOOK_SECRET}` 등). 웹훅은 **샘플 페이로드로 테스트**.
- 구독 상태 저장·조회·게이팅 로직을 코드로 완성하고 `./gradlew build test` 통과까지가 완료 기준.

## 요구사항 (EARS)
- WHEN RevenueCat 가 웹훅(구매/갱신/취소/만료/환불)을 POST 하면, THE 시스템 SHALL Authorization 검증 후 이벤트 타입에 따라 해당 user 의 subscriptions 레코드를 갱신한다.
- WHEN 인증된 사용자가 `GET /subscription` 을 호출하면, THE 시스템 SHALL 현재 구독 상태(active/in_grace/expired/none, 만료일, tier)를 반환한다.
- WHERE 사용자가 활성 구독이 없으면, THE 시스템 SHALL 프리미엄 전용 엔드포인트 접근을 403(구독 필요)로 막는다.
- THE 시스템 SHALL subscriptions 를 `user_id` 로 격리한다(멀티테넌시 원칙 준수).

## 구현 항목
1. **Flyway 마이그레이션**: `subscriptions(id, user_id FK, store[apple|google], product_id, entitlement, status[active|in_grace|expired|none], original_transaction_id, current_period_end timestamptz, updated_at)`. 현재 상태 테이블 + (옵션) `subscription_events` 이력 로그. `user_id` 기준 1활성구독.
2. **POST /webhooks/revenuecat**: `Authorization: Bearer ${REVENUECAT_WEBHOOK_SECRET}` timing-safe 검증(기존 internal-auth 패턴 재사용 가능). 이벤트 파싱 → 상태 매핑: INITIAL_PURCHASE/RENEWAL/PRODUCT_CHANGE→active, CANCELLATION→active 유지(기간말 만료), EXPIRATION→expired, BILLING_ISSUE→in_grace, REFUND→none. `app_user_id` 로 user 매핑.
3. **GET /subscription**: 현재 사용자 구독 상태 DTO 반환.
4. **SubscriptionService + 게이팅**: 활성 구독 확인 헬퍼(예: `@RequiresSubscription` 어노테이션 또는 서비스 가드). 프리미엄 엔드포인트에 적용 지점 1곳 이상 예시.
5. **테스트**: 웹훅 이벤트별 상태전이 단위테스트, 게이팅 통합테스트(Testcontainers), `user_id` 격리 테스트.
6. **OpenAPI**: `/subscription` 노출(springdoc).

## 인수기준
- [ ] subscriptions 마이그레이션 + 엔티티/리포지토리(`user_id` 격리)
- [ ] 웹훅 5종 이벤트 상태전이 테스트 통과
- [ ] `GET /subscription` 동작 + OpenAPI 노출
- [ ] 게이팅 헬퍼 + 비구독 403 통합테스트
- [ ] `user_id` 격리 테스트 통과
- [ ] `./gradlew build test` 전체 통과
- [ ] 시크릿은 env placeholder, 실제 RevenueCat 계정/스토어/배포 없음
- [ ] 한국어 문서(spec/주석 외 산출물) + 변경 파일만 커밋

## 비고
- 앱(APP-014)과 계약: 앱은 RevenueCat SDK로 구매하고, 백엔드는 웹훅으로 상태를 받는다(서버가 SSOT). 앱은 `GET /subscription` 으로 상태 동기화.
