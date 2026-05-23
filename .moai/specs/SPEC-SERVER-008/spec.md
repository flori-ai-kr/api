# SPEC-SERVER-008 — 예약 + 캘린더 API

> status: DOING · deps: 005, 007 · Phase 1 (M2 도메인)

## 목표

예약(픽업) 도메인 + 캘린더 이벤트 도메인 REST API. 예약 CRUD/매출 전환/픽업완료/자동완성,
캘린더 이벤트 CRUD, `@Scheduled` 일일 픽업 요약(08:00 KST)·개별 리마인더(reminder_at) 푸시.

## 범위 (In)

- **예약**: `Reservation` CRUD + 월별 조회 + 다가오는 예약 + 발동 리마인더(48h 윈도) + 제목/메모 자동완성.
- **매출 연동**: 예약→매출 전환(`SaleService`로 생성 후 sale_id 연결), 매출에 픽업 추가(고객정보 상속), 매출별 예약 조회.
- **픽업 완료**: `pickup_completed` 처리. reminder_at 변경 시 `reminder_sent` 리셋.
- **캘린더**: `CalendarEvent` CRUD + 월 범위 겹침 조회 + 날짜 범위 검증(end ≥ start).
- **스케줄 푸시**: `ReservationNotificationService` — 5분마다 도달 리마인더 발송 + `reminder_sent` 마킹(중복 방지), 매일 08:00 KST 사용자별 당일 픽업 요약. 토큰은 push_subscriptions 조회, 영구실패 토큰 비활성화. SPEC-004 `PushService` 사용.
- 모든 테넌트 쿼리 `TenantContext` 격리. 스케줄러는 시스템 작업(전체 테넌트).

## 범위 밖 (Out)

- 예약 목록의 매출 필드 조인 enrichment(sale_date/product_category 등) — v1은 `saleId`만 노출, 앱이 필요 시 매출 조회.
- 푸시 구독(push_subscriptions) CRUD (→ SPEC-012). 여기서는 토큰 읽기/비활성화만.
- 대시보드의 다가오는 예약/리마인더 위젯 집계 (→ SPEC-013, 단 조회 API는 제공).

## 인수 기준

1. `./gradlew build test` 통과(LocalTime/Instant 컬럼 validate 포함).
2. 예약 CRUD + 월별/다가오는/리마인더(48h) 조회.
3. reminder_at 변경 시 reminder_sent 리셋.
4. 예약→매출 전환 시 sale 생성 + 예약 sale_id 연결. 매출에 픽업 추가 시 고객정보 상속.
5. 픽업 완료 처리, 매출별 예약 조회.
6. 캘린더 CRUD + 월 겹침 조회 + 범위 검증.
7. 스케줄러: 도달 리마인더만 reminder_sent 마킹(미도달 제외), 일일 요약 사용자 집계.
8. **멀티테넌시 격리**: 다른 user의 예약/이벤트 조회 차단.

## 설계 메모

- 스케줄러는 시스템 작업이라 전체 테넌트의 due 리마인더/당일 예약을 조회(user 필터 없음). reminder_sent로 멱등(중복 발송 방지).
- 리마인더 발송/일일 요약 로직은 `@Scheduled` 트리거와 분리(`markAndNotifyDueReminders(now)`, `sendDailySummary(today)` 직접 호출 테스트).
- 푸시는 `PushService`(로컬은 LoggingPushService 폴백)로 추상화 — 토큰 없어도 reminder_sent 마킹은 진행.
- 검증은 Zonky 임베디드 PG. 패턴은 기존 도메인 동일.
