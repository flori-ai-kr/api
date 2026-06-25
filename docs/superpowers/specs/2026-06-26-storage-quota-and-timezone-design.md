# 설계 — 이미지 스토리지 쿼터 + DB/운영 시간대 KST 정합

- 작성일: 2026-06-26
- 상태: 설계 확정(리뷰 대기)
- 범위: 인프라 백로그 "고민필요" 2건 — ① DB/운영 시간대(인프라) ② 이미지 인당 스토리지 쿼터(기능)
- 관련 레포: `api`(주), `web`(UI), `aws-infra`(②의 일부는 인프라/운영)

> 이 문서는 브레인스토밍 산출물(설계 SSOT)이다. 구현 단계 계획은 writing-plans에서 별도 생성한다.

---

## 1. 배경

인프라 점검 중 도출된 5건 백로그 중, 결정이 필요한("고민필요") 2건을 먼저 설계한다.

- **2번(시간대)**: "DB를 KST로 바꾸자"는 요청이었으나, 점검 결과 **저장은 이미 UTC(정석)·앱 로그·DB 서버 timezone 모두 KST**로 동작 중임이 확인됐다. 사용자의 실제 불편은 "**DataGrip 직접 조회가 UTC로 보임**"과 "**호스트/운영 로그가 UTC**" 두 가지뿐이다. 따라서 저장 구조를 바꾸지 않고 **사람이 보는 표면만 KST로 정렬**한다.
- **1번(쿼터)**: 목적은 **S3 비용통제**. 현재 인당 사용량 추적/제한이 전혀 없다. 갤러리(photo-cards) 기준 인당 기본 3GB 한도 + 관리자 개별 증설.

### 검증으로 확인된 현황 (출발점)

| 대상 | 현재 상태 | 근거 |
|------|-----------|------|
| 저장 컬럼 | `TIMESTAMPTZ`(절대시각, 내부 UTC) | DDL 116컬럼 전부 TIMESTAMPTZ |
| JPA | `hibernate.jdbc.time_zone: UTC` + `Instant` | application.yml, BaseEntity |
| DB 서버 `timezone` | **이미 `Asia/Seoul`** | `SHOW timezone` → Asia/Seoul, `now()` → `+09` |
| api 앱 로그 | **이미 KST(+09:00)** | 컨테이너 `TZ=Asia/Seoul`, 구조화 JSON 로그 `@timestamp +09:00` |
| **EC2 호스트 OS** | **UTC** | `date` → UTC |
| 모든 컨테이너 | `TZ=Asia/Seoul` 고정 | app(api·web·pg)·ai(litellm·langfuse 등) compose 전부 |
| 업로드 사용량/쿼터 | **없음**(추적·제한 0) | photos 도메인 grep |
| presign 요청 | 클라가 `FileMetaRequest.size` 전송 | `PhotoCardService.validateImageMeta(size)` |
| `PhotoFile`(jsonb) | `url`, `originalName`만 — **size 미저장** | `PhotoCard.kt` |

---

## 2. 시간대 KST 정합 (인프라)

### 원칙
**저장은 UTC 그대로 유지(정석: 절대시각 저장 → 가장자리에서 로컬 변환).** 사람이 보는 표면만 KST.

### 변경 사항

| # | 작업 | 대상 | 비고 |
|---|------|------|------|
| 2-1 | **변경 없음** | api 코드/JPA/DDL | `hibernate.jdbc.time_zone=UTC` + `Instant`/TIMESTAMPTZ 유지. 손대면 이중변환 버그 |
| 2-2 | `timedatectl set-timezone Asia/Seoul` | **dev-app·dev-ai·prod-app** | 즉시 반영, 재시작 불필요. **dev 먼저 → 확인 → prod** |
| 2-3 | `ALTER DATABASE flori SET timezone='Asia/Seoul';` | dev·prod pg | 서버 기본값을 KST로 핀(컨테이너 TZ env 빠져도 보장). 앱 무관(JDBC UTC 명시) |
| 2-4 | DataGrip 데이터소스 timezone → `Asia/Seoul` | 로컬 IDE | 정확한 설정 경로는 구현 시 JetBrains 공식 문서로 확정 |
| 2-5 | 정합 규칙 문서화 | aws-infra | "호스트=KST / DB tz=KST / 저장=UTC" 명문화 |

> prod-ai 인스턴스는 현재 미존재 → 대상 3대.

### 사이드이펙트 분석 (왜 안전한가)
- **컨테이너 영향 0**: 모든 flori 컨테이너가 `TZ=Asia/Seoul`을 고정 → 호스트 OS TZ를 보지 않음.
- **시간 민감 작업 없음**: 호스트에 user crontab·`/etc/cron.d` 없음. systemd 타이머는 OS 기본(logrotate 자정·sysstat·fstrim)뿐 → KST로 밀려도 무해.
- **절대시각 불변**: `timedatectl`은 표시 렌즈만 바꾸고 시스템 클럭(UTC epoch)은 그대로 → **AWS SigV4 서명·TLS 인증서·JWT 만료·DB timestamptz 전부 영향 없음**.
- **CloudWatch**: `/var/log/messages` 텍스트 시각만 KST. 이벤트 시각은 ingestion 기반이라 스큐 없음.

### 검증 방법
- 2-2 후: `date` → KST 표기, `docker logs api`/`web` 여전히 +09:00, `curl actuator/health` 200(앱 정상).
- 2-3 후: 새 psql 세션 `SHOW timezone` → Asia/Seoul.
- 2-4 후: DataGrip에서 `created_at`이 +09로 표시.
- 앱 회귀: 통계/예약 등 시간 의존 API 스모크(값 불변 확인).

---

## 3. 이미지 인당 스토리지 쿼터 (기능)

### 목표·정책 (확정)
- 목적: **S3 비용통제**
- 측정 범위: **갤러리(photo-cards)만** (profiles·business-licenses·community 제외 — 용량 비중 미미)
- 기본 한도: **3GB 고정**(= 3,221,225,472 bytes), 전 유저 동일. 플랜별 차등은 **미적용(보류)**, 단 per-user 증설은 가능
- 100% 초과: **하드 차단**(증설 전까지 업로드 불가)
- 90% 이상: 경고 + **관리자 증설 요청** 유도
- 집계 방식: **카운터 + 주기 정합(A안)**
- 증설: 점주 요청 → Discord 알림 + 관리자 콘솔 수동 상향 (support/인증 심사 패턴 재사용)

### 3.1 데이터 모델

**신규 테이블 `user_storage`** (인당 카운터 1행)
```sql
CREATE TABLE user_storage (
  user_id     BIGINT      PRIMARY KEY REFERENCES users(id),
  used_bytes  BIGINT      NOT NULL DEFAULT 0,
  quota_bytes BIGINT      NOT NULL DEFAULT 3221225472,  -- 3 * 1024^3
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```
- 없으면 최초 접근 시 기본값(used=0, quota=3GB)으로 생성(get-or-create).
- 엔티티는 `BaseEntity`(created_at/updated_at 자동) 패턴. `ddl-auto=validate`에 맞춰 DDL은 `docs/sql/all-tables-ddl.sql`에 추가.

**`PhotoFile`(jsonb)에 `size: Long` 추가**
```kotlin
data class PhotoFile(
    val url: String = "",
    val originalName: String = "",
    val size: Long = 0,   // 신규 — 감분·정합의 정확도 근거
)
```
- 클라가 이미 presign 때 보내는 size를 카드 저장 시 `PhotoFile`에 영속화.
- 구(舊) 카드는 size=0 → 정합 작업이 점진 보정(아래 3.4).

**신규 테이블 `storage_increase_requests`** (증설 요청 이력)
```sql
CREATE TABLE storage_increase_requests (
  id              BIGSERIAL   PRIMARY KEY,
  user_id         BIGINT      NOT NULL REFERENCES users(id),
  reason          TEXT,
  status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING | RESOLVED
  resolved_bytes  BIGINT,                                  -- 관리자가 상향한 quota
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### 3.2 강제(enforcement) 지점

`StorageQuotaService`(신규, `common` 또는 `photos` 도메인) — 단일 책임: 쿼터 조회·검사·증감.

- **presign 발급 시 검사** — `PhotoCardService.createUploadTargets`(신규/기존카드 **두 오버로드 모두**):
  - `used_bytes + Σ(요청 file.size) > quota_bytes` → **하드 차단**: 신규 에러 `E-STORAGE-001`(저장공간 부족).
  - 차단 응답에 `usedBytes/quotaBytes/percent` 동봉(웹이 증설요청 유도).
- **증감(charge/refund)** — 실제 영속 시점 기준:
  - `create`: `used_bytes += Σ(저장 PhotoFile.size)`
  - `update`(photos 교체): `used_bytes += (신규 Σ − 기존 Σ)`
  - `delete`(카드)·`deletePhoto`(단건): `used_bytes -= 해당 Σ`
  - 증감은 원자적 단일 UPDATE(`SET used_bytes = used_bytes + :delta`).
- **경합(TOCTOU)**: 병렬 presign 2건이 동시에 통과할 수 있으나 비용통제 목적상 허용(다음 업로드에서 차단 + 정합 작업이 보정).

### 3.3 조회 API / 웹·어드민

**점주 API**
- `GET /storage/usage` → `{ usedBytes, quotaBytes, percent, status }` (`status`: `OK` | `WARN`(≥90%) | `FULL`(≥100%))
- `POST /storage/increase-request { reason }` → 요청 생성 + Discord 알림(**SUPPORT 채널 재사용** — 증설은 운영 지원성 요청. 신규 STORAGE 채널은 불필요)

**관리자 API** (`@RequiresAdmin`)
- `GET /admin/storage/requests` → 증설 요청 목록(닉네임·가게명·현재 used/quota 포함)
- `PATCH /admin/storage/users/{userId}/quota { quotaBytes }` → `quota_bytes` 상향 + 연관 요청 `RESOLVED` 처리

**web (점주)**
- 갤러리/업로드 화면: 사용량 바(used/quota), ≥90% 경고 배너 + "증설 요청" 버튼 → 사유 입력 모달 → POST.
- 업로드 차단(FULL): 명확한 안내 + 증설요청 CTA. presign 401/422 에러 핸들링.

**web (어드민 콘솔)**
- 증설 요청 목록 + 개별 `quota_bytes` 상향 액션.

### 3.4 정합(reconciliation) 작업

`@Scheduled` + `JobRunRecorder`(기존 인프라), `JobNames`에 식별자 추가.

- **1차(핵심) — DB 합산 true-up**: 각 유저의 카드 `PhotoFile.size` 합으로 `used_bytes` 재계산·보정. 카운터 드리프트(증감 누락·구 카드 size=0) 교정. 항상 가능.
- **2차(선택) — S3 고아 정리**: DB에 참조 없는 S3 객체(업로드 후 카드 미저장 등) 탐지·정리. 비용통제의 실효. **키 네이밍 개선(3.5)** 전제.

### 3.5 키 네이밍 개선 (권장, 정합 정확도용)
- 현재 기존카드 업로드 키 `photo-cards/{cardId}/…` → userId 역산 불가. 신규카드는 `photo-cards/u{userId}/…`.
- **향후 신규 업로드는 `photo-cards/u{userId}/{cardId}/…`로 통일** → S3 직접 정합·고아정리 용이. 구 객체는 DB(cardId→userId) 매핑으로 처리.

### 3.6 에러 코드
- 신규 도메인 `STORAGE`: `E-STORAGE-001` 저장공간 한도 초과(업로드 차단). `ERROR_CODES.md` 갱신.

### 3.7 테스트 (api)
- `StorageQuotaService`: 한도 미만 통과 / 한도 초과 차단(E-STORAGE-001) / 90% WARN 플래그.
- 증감: create += / delete −= / update 델타 / deletePhoto −=.
- 정합 작업: PhotoFile.size 합으로 used_bytes 재계산.
- 증설요청: 생성 → Discord 알림 / 관리자 quota 상향 → 요청 RESOLVED.
- 멀티테넌시: user_storage 인당 격리(타 유저 사용량/쿼터 접근 불가).

---

## 4. 범위 밖 / 보류
- 플랜별 차등 한도(무료/유료) — 보류(per-user 증설로 우선 대응).
- S3 객체 단위 자동 삭제(고아 sweep)는 **선택 구현**(키 네이밍 통일 후).
- 구 `photo-cards/{cardId}/` 키의 일괄 마이그레이션 — 미수행(신규만 통일, 구건 DB 매핑).

## 5. 미해결 질문
- 없음(설계 결정 모두 확정). 구현 시 확정할 디테일(설계 영향 없음): `users` 테이블 PK/FK 정확 명칭, DataGrip 설정 경로(JetBrains 공식 문서).
