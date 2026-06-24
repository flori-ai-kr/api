# 사업자 인증 알림톡(접수·승인·거절) + 발송 가시화 — 설계

- 작성일: 2026-06-24
- 브랜치: `session2-biz-approval-notify` (api · web)
- 상태: 설계 승인 대기 → 구현 계획(writing-plans) 전 단계

## 1. 배경 / 목표

점주가 사업자등록증을 제출하고 운영자가 심사하는 플로우는 이미 구현돼 있다(제출 → Discord 알림, 승인/거절 → `BusinessVerificationReviewedEvent`). 다만 **점주에게 카카오 알림톡으로 결과를 통지하는 부분은 승인 케이스만** 코드가 있었고, 실제 연결된 솔라피 템플릿이 없어 동작하지 않았다(미설정 폴백 = 콘솔 로그).

최근 카카오 비즈니스 채널(발신프로필) 연동이 승인되어, 알림톡 템플릿을 등록하고 마무리한다.

**이번 작업 목표**
- 점주가 인증 **제출** 시 "접수 안내" 알림톡 발송 (신규)
- 운영자 **승인** 시 "완료 안내" 알림톡 발송 (기존 코드 + env 주입)
- 운영자 **거절** 시 "결과(반려 사유) 안내" 알림톡 발송 (신규)
- 위 세 발송의 **성공/실패 결과를 운영 콘솔 발송 로그에 기록**(가시화)

## 2. 범위 / 비범위

**범위**
- A. 접수 알림톡 발송 경로 추가
- B. 승인 알림톡 env 연결(코드 변경 없음, 정합 확인)
- C. 거절 알림톡 발송 경로 추가
- D. 세 발송 결과를 `NotificationSendLog`에 기록 → 기존 `/console/notification-logs` 화면 자동 노출

**비범위(이번 제외)**
- 운영콘솔 "알림톡 재발송" 버튼 (다음 세션 후보)
- 거절 사유 표준화/사유 코드화 (현행 자유 입력 유지)
- web 신규 화면 (기존 발송 로그 화면 재사용)

## 3. 외부 의존성 (솔라피 / 카카오)

알림톡은 **템플릿마다 카카오 검수**가 필요하며(영업일 1~3일), 검수 통과 전에는 미설정 폴백으로 안전하게 빌드·머지된다. 검수 통과 후 배포 env에 값만 주입하면 실발송된다.

| 템플릿 | 변수 | 상태 | 매핑 env(이름만) |
|---|---|---|---|
| 접수 안내 | `#{상호}` | 등록·검수중 | `SOLAPI_SUBMITTED_TEMPLATE_ID` (신규) |
| 승인 완료 | `#{상호}` | 등록·검수중 | `SOLAPI_APPROVAL_TEMPLATE_ID` (기존) |
| 결과(반려) 안내 | `#{상호}`, `#{사유}` | **등록 필요** | `SOLAPI_REJECTED_TEMPLATE_ID` (신규) |

> 시크릿/실값(API Key·Secret·발신번호·pfId·templateId)은 **이 문서에 적지 않는다.** 코드(`application.yml`)는 `${SOLAPI_*}` 참조만 두고, 실값은 배포 env(EC2 docker-compose / aws-infra)에만 주입한다. (세션 작업용 실값은 repo 밖 스크래치패드에 보관.)

전체 솔라피 env(이름):
`SOLAPI_API_KEY`, `SOLAPI_API_SECRET`, `SOLAPI_SENDER_PHONE`, `SOLAPI_PF_ID`,
`SOLAPI_APPROVAL_TEMPLATE_ID`, `SOLAPI_SUBMITTED_TEMPLATE_ID`(신규), `SOLAPI_REJECTED_TEMPLATE_ID`(신규)

## 4. 아키텍처 / 플로우

기존 패턴을 그대로 따른다: **도메인 이벤트 → `@TransactionalEventListener(AFTER_COMMIT)` → `SolapiNotifier`(best-effort) → 발송 결과 기록**.

```
[제출]  POST /verification/business
  → BusinessVerificationService.submit() (PENDING 저장)
  → BusinessVerificationSubmittedEvent
  → BusinessVerificationEventListener (AFTER_COMMIT)
      ├─ Discord VERIFICATION 알림 (기존)
      └─ phone 조회 → SolapiNotifier.sendBusinessSubmitted(phone, 상호)   ★신규

[심사]  POST /admin/verifications/{id}/approve | /reject
  → AdminVerificationService → BusinessVerificationReviewedEvent(approved, reason)
  → BusinessVerificationReviewedListener (AFTER_COMMIT)
      ├─ Discord VERIFICATION 알림 (기존)
      ├─ approved  → SolapiNotifier.sendBusinessApproved(phone, 상호)     기존
      └─ !approved → SolapiNotifier.sendBusinessRejected(phone, 상호, 사유) ★신규

[가시화]  각 SolapiNotifier.sendXxx 내부에서 발송 직후
  → NotificationSendRecorder.record(source="business_verification", type="alimtalk", ...)  ★신규
  → notification_send_logs append → /console/notification-logs 자동 노출
```

전화번호는 두 리스너 모두 `UserProfileRepository.findById(userId).phoneNumber`로 조회한다(승인 리스너의 기존 방식과 동일). 빈 값이면 `SolapiNotifier`가 발송 스킵(경고 로그).

## 5. 변경 상세 (파일별)

### A. 접수 알림톡
- `common/notification/solapi/SolapiProperties.kt`: `submittedTemplateId` 필드 추가. `isConfigured()`는 **공통 자격(apiKey/secret/senderPhone/pfId)만** 검사하도록 분리하고, 템플릿ID는 각 send 메서드에서 개별 확인(템플릿별 검수 시점이 달라 일부만 비어도 나머지는 발송 가능해야 함).
- `resources/application.yml`: `solapi.submitted-template-id: ${SOLAPI_SUBMITTED_TEMPLATE_ID:}` 추가.
- `common/notification/solapi/SolapiNotifier.kt`: `sendBusinessSubmitted(phone, storeName)` 추가. 기존 `sendBusinessApproved`와 동일 구조(HMAC 인증, `kakaoOptions{ pfId, templateId=submittedTemplateId, variables{ "#{상호}": storeName }, disableSms=false }`), `@Async` + best-effort.
- `verification/listener/BusinessVerificationEventListener.kt`: `SolapiNotifier` + `UserProfileRepository` 주입. Discord 발송 뒤 phone 조회 → `sendBusinessSubmitted` 호출. (리스너에는 `@Async`를 추가하지 않는다 — `SolapiNotifier`/`DiscordNotifier`가 각자 비동기.)

### B. 승인 알림톡 (코드 변경 없음)
- 기존 `sendBusinessApproved` + `approvalTemplateId` 그대로. 변수 `#{상호}` 정합 확인 완료. **배포 env에 `SOLAPI_APPROVAL_TEMPLATE_ID` 주입만** 하면 동작.

### C. 거절 알림톡
- `SolapiProperties.kt`: `rejectedTemplateId` 필드 추가.
- `application.yml`: `solapi.rejected-template-id: ${SOLAPI_REJECTED_TEMPLATE_ID:}` 추가.
- `SolapiNotifier.kt`: `sendBusinessRejected(phone, storeName, reason)` 추가. `variables = { "#{상호}": storeName, "#{사유}": reason }`. 나머지 구조 동일.
- `admin/listener/BusinessVerificationReviewedListener.kt`: 현재 거절 시 Discord만 → `!approved` 분기에 phone 조회 후 `sendBusinessRejected(phone, businessName, reason)` 추가(`reason`은 이벤트가 보유).

### D. 발송 결과 가시화 (기록)
- 기존 `admin/entity/NotificationSendLog.kt` + `NotificationSendLogService.record(...)` 재사용.
- **의존성 방향 보호**: `SolapiNotifier`는 `common`에 있고 `NotificationSendLog`/`Service`는 `admin`에 있어 `common → admin` 역방향이 된다. 이를 피하기 위해 **`common`에 포트 인터페이스 `NotificationSendRecorder`** 를 정의하고, `SolapiNotifier`는 이 인터페이스에만 의존한다. 구현체(어댑터)는 `admin` 패키지에 두고 `NotificationSendLogService`로 위임한다(Spring DI). 인터페이스가 비어 있어도(빈 미등록) 동작하도록 nullable/옵셔널 주입 또는 no-op 기본 구현을 둔다.
- 기록 규약: `source="business_verification"`, `type="alimtalk"`, 성공 시 `sentCount=1/failedCount=0`, 실패 시 `sentCount=0/failedCount=1` + `errorMessage`, `targetUserId=userId`, `title`=템플릿 종류(접수/승인/거절). 기록 실패는 비차단(best-effort).

### web (거의 없음)
- 기존 `/console/notification-logs`(발송 로그) 화면이 `type/source/status` 필터로 조회하므로 **자동 노출**. (선택) 필터 드롭다운에 `source=business_verification` / `type=alimtalk` 라벨 추가 — 우선순위 낮음, 시간 남으면.

## 6. 보안

- 시크릿은 전부 env. 코드/문서/깃에 실값 금지(`application.yml`은 `${ENV}` 참조).
- 전화번호 로그 마스킹(기존 `maskPhone`) 유지. 발송 로그 `body`/`metadata`에 전화번호 평문 저장 금지.
- 멀티테넌시: 발송은 운영자 트리거(cross-tenant) — `targetUserId`만 기록.

## 7. 테스트 (Zonky embedded PostgreSQL)

- **미설정 폴백**: 템플릿ID/키 공백이면 send가 외부 호출 없이 스킵(승인 기존 테스트 패턴 따름).
- **제출 → 접수 발송 경로**: 제출 이벤트 발행 시 `sendBusinessSubmitted` 호출 검증(Notifier mock).
- **거절 → 거절 발송 경로**: 거절 시 `sendBusinessRejected(…, reason)` 호출 + 승인 시 미호출 검증.
- **발송 기록**: 성공/실패에 따라 `NotificationSendRecorder.record`가 올바른 `source/type/status`로 호출되는지(어댑터 mock 또는 통합).
- 기존 `AdminVerificationIntegrationTest` 회귀 통과.
- 게이트: `./gradlew build test` (ktlint + detekt + JaCoCo 80%).

## 8. 배포 / 검증 순서

1. (병렬) 거절 템플릿 등록 → 검수 큐. 접수·승인은 검수중.
2. 코드 머지(미설정 폴백이라 검수 전 안전).
3. 세 템플릿 검수 통과 확인.
4. 배포 env(dev 우선)에 `SOLAPI_*` 7종 주입.
5. dev에서 실발송 e2e: 테스트 계정으로 제출 → 접수 알림톡 수신 확인 → 승인/거절 → 각 알림톡 수신 + `/console/notification-logs`에 기록 확인.
6. 이상 없으면 prod env 주입.

## 9. 열린 결정 (구현 계획에서 확정)

- D의 기록 메커니즘: 포트 인터페이스(권장, 본 문서 채택) vs 발송 결과 이벤트 발행. 본 설계는 **포트 인터페이스**로 확정.
- `접수` 건의 `disableSms`: 비용 최적화로 `true`(SMS 폴백 미사용) 가능하나, 일관성·전달 보장을 위해 **세 건 모두 `false` 유지**(저빈도라 비용 영향 무시 가능).
