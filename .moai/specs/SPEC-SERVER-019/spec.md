# SPEC-SERVER-019 — 스케줄러 멱등성 + 실패격리 (D1+D2)

## 목표
`@Scheduled` 잡(리마인더·일일요약·고정비생성)에 **중복 발송 방지(멱등성, D1)** 와 **건별 실패 격리(D2)** 를 적용한다. 출처: `onetime/batch`의 "발송로그 + NOT EXISTS 멱등성" 및 "잡/대상자 2중 실패격리" 패턴.

## 배경(기존 갭)
- **일일 요약(08:00)**: 멱등성 전무 → 스케줄러 재기동/중복 트리거 시 같은 사용자에게 중복 발송.
- **리마인더(5분)**: `reminder_sent` 플래그로 멱등하나, `forEach` 중 한 건 실패 시 메서드 `@Transactional`이 전체 롤백 → 이미 발송된 건도 마킹이 풀려 다음 회차에 재발송.
- **고정비 생성(00:30)**: `ON CONFLICT`로 이미 멱등하나, 단일 `@Transactional` 내 한 insert 실패 시 PG 트랜잭션 abort로 나머지도 실패.

## 핵심 설계
- PostgreSQL은 **트랜잭션 내 한 문장 오류 시 트랜잭션 전체가 abort**된다. 따라서 진짜 건별 격리를 위해 스케줄 처리 메서드의 **메서드 레벨 `@Transactional`을 제거** → 각 작업(save/claim/insert/구독 비활성화)이 독립 커밋되게 한다.
- 잡 레벨 격리(한 회차 실패가 다음 회차를 막지 않음)는 **Spring 스케줄러가 메서드별로 이미 제공** → 별도 래퍼 불필요(hazel은 잡마다 별도 @Scheduled 메서드).
- 건별 catch는 코드베이스 컨벤션(구체 예외)에 맞춰 `DataAccessException`(DB 실패 = 실제 격리 대상)으로 한정. (detekt `TooGenericExceptionCaught` 회피 겸)

## 구현
### D1 — 멱등성
- `V5__notification_log.sql`: `notification_log(user_id, notification_type, dedup_key)` UNIQUE 테이블.
- `ReservationNotificationService.claimOnce()`: `INSERT ... ON CONFLICT DO NOTHING`의 갱신행수==1 → 원자적 claim. 일일 요약은 `(userId, 'daily_summary', 날짜)`로 1회만 발송(at-most-once).

### D2 — 실패 격리
- `markAndNotifyDueReminders`: 건별 try-catch(`DataAccessException`), 성공 건만 `reminder_sent` 마킹·독립 save. `@Transactional` 제거.
- `sendDailySummary`: 사용자별 try-catch, claim 성공 시에만 발송. `@Transactional` 제거.
- `RecurringExpenseGenerator.generateForDate`: 템플릿별 try-catch, `@Transactional` 제거(insert 독립 커밋).

## 인수기준
- [x] `notification_log` 마이그레이션 + 원자적 claim
- [x] 일일 요약 멱등성 — 같은 날짜 2회 호출 시 2번째는 0건 발송(테스트)
- [x] 리마인더·요약·고정비 생성 건별 격리(@Transactional 제거, DataAccessException catch)
- [x] 기존 동작 보존 — 리마인더 마킹/요약 집계 테스트 통과
- [x] `./gradlew build test` 그린 — 168 테스트(+1, 0 실패/0 스킵)

## 트레이드오프
- 일일 요약은 **at-most-once**(claim 후 발송): 발송 실패 시 그날 요약은 누락될 수 있으나, 중복 스팸을 막는 쪽을 택함(비핵심 다이제스트). 리마인더는 발송 성공 후 마킹이라 실패 시 다음 회차 재시도.
- `@Transactional` 제거로 같은 메서드 내 작업들의 원자성은 포기하나, 시스템 배치 특성상 **건별 독립 진행이 더 바람직**(한 건 실패가 전체를 막지 않음).
