# HANDOFF — Hazel Server

> 각 세션은 작업 후 이 파일을 갱신한다. 다음 세션은 ROADMAP.md + 이 파일을 읽고 이어간다.

## 현재 상태

- **부트스트랩 완료** (2026-05-23). repo 생성 + 문서(CLAUDE/ROADMAP/DESIGN) + 초기 커밋.
- 코드 구현은 **아직 시작 전**. Gradle/Spring 프로젝트 미생성 상태.

## 다음 할 일

- **SPEC-SERVER-001 (프로젝트 스켈레톤)** 부터 시작.
- 이 repo를 cwd로 둔 세션에서 CLAUDE.md의 "loop 시작 프롬프트"를 실행하면 자동 진행.

## 사전 준비물 (실제 동작에 필요 — 구현과 별개로 사용자가 채울 환경변수)

코드는 `${ENV}` 참조로 작성하고, 실제 값은 배포 시 주입한다. 구현 단계에서 막히지 않음(로컬은 docker-compose Postgres로 대체 가능).

- `DB_URL`, `DB_USER`, `DB_PASSWORD` (AWS RDS PostgreSQL)
- `JWT_SECRET` (서명키)
- `AWS_REGION`, `S3_BUCKET`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `CLOUDFRONT_DOMAIN`
- `FCM` 서비스 계정 JSON 경로/내용
- `DISCORD_WEBHOOK_URL`
- `INTERNAL_API_KEY` (≥32자)

## 블로커

- 없음.

## 로그 (최신이 위로)

- 2026-05-23 — 부트스트랩. ROADMAP 13개 SPEC(Phase1) + 1개(Phase2) 정의.
