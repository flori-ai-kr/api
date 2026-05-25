# flori-server

Flori Server — the Spring REST backend for the Flori florist SaaS. It owns the data and the rules: sales, expenses, customers, reservations, deposits, gallery, and statistics for independent flower shops, exposed as a mobile-callable REST API on its own AWS infrastructure.

This is the **source of truth** for the whole Flori system. Multi-tenancy and subscription gating are enforced here — every query is isolated by the `user_id` extracted from the caller's JWT. There is no database RLS, so the application is the only line of defense. The AI service (`flori-ai/ai`) holds no direct database access; it calls this API as LangGraph tools, forwarding the user's JWT unchanged.

Server-side calculations are authoritative: card fees (`amount * (1 - fee_rate/100)`), deposit-due business days, and expense totals (`unit_price * quantity`) are computed here, and the client only displays them.

Docs:
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — architecture & technology rationale, Mermaid diagrams (Korean)
- [docs/PATTERNS.md](docs/PATTERNS.md) — layer patterns, multi-tenancy, new-domain recipe, conventions (Korean)
- [docs/KOTLIN.md](docs/KOTLIN.md) — Kotlin/Spring idioms used in this repo (Korean)
- [docs/DESIGN.md](docs/DESIGN.md) — design SSOT, background & scope (Korean)
- [ROADMAP.md](ROADMAP.md) — SPEC list, order, status (Korean)
- [HANDOFF.md](HANDOFF.md) — last session state, next steps (Korean)
- [CLAUDE.md](CLAUDE.md) — autonomous execution protocol, stack, security checklist (Korean)

## Domains

| Domain | Responsibility |
|--------|----------------|
| **auth** | Sign-up / login, JWT issue, refresh rotation, `/me` |
| **sales** | Sales records + server-side deposit calculation |
| **expenses** | Expenses + recurring fixed costs (auto-generated via `@Scheduled`) |
| **customers** | Customers (find-or-create, real-time stats) |
| **reservations / calendar** | Reservations (sale conversion, pickup) · calendar (reminder push) |
| **deposits** | Card deposit reconciliation |
| **photos** | Gallery (presigned upload) · tags |
| **insights** | Trend / share reads · scrap · internal ingest |
| **settings** | Card companies · sales/expense settings · bottom bar · push subscription |
| **dashboard** | Today / month aggregation · native-SQL statistics |

## Responsibility split

| Layer | Responsibility |
|-------|----------------|
| **flori-ai/server (this project)** | Spring REST API. Source of truth for data, multi-tenancy & subscription gating, `user_id` isolation. The verified surface the AI tools wrap |
| flori-ai/ai | AI orchestration — tool-call loop, vision OCR, voice session, confirmation cards, usage caps. Holds no DB access; calls this API with the user's JWT |
| flori-ai/mobile | React Native app. JWT issuer (login UI), confirmation-card UI, voice I/O |
| flori-ai/web | Next.js PWA admin for the same shops |

## Tech stack

| Area | Tech |
|------|------|
| Language / build | Kotlin + Gradle (Kotlin DSL), Java 21 toolchain |
| Framework | Spring Boot 3.5 |
| Data access | Spring Data JPA + Hibernate (jsonb/array via hypersistence-utils), native SQL for statistics |
| Database | AWS RDS PostgreSQL |
| Migrations | Flyway |
| Auth | Spring Security + custom JWT (access + refresh rotation), BCrypt |
| Validation | Jakarta Bean Validation |
| Storage | AWS S3 + CloudFront (presigned PUT URLs) |
| Push | FCM (Firebase Admin SDK) |
| Scheduling | Spring `@Scheduled` (KST) |
| Error reporting | `@ControllerAdvice` standard responses + Discord webhook |
| API docs | springdoc-openapi (Swagger UI) — the contract the app reads |
| Lint & test | ktlint / detekt / JUnit + Zonky embedded PostgreSQL |

## Development

```bash
./gradlew build test     # ktlint + detekt + full test suite (embedded PostgreSQL)
./gradlew ktlintFormat   # auto-format
./gradlew bootRun        # run locally (profile: local, graceful env fallback)
open http://localhost:8080/swagger-ui.html   # API contract
```

Tests run against Zonky embedded PostgreSQL, so no local database is required for `./gradlew test`. Flyway applies the schema on boot; health check at `GET /health`.

## Security model

- **Multi-tenancy is the #1 priority.** With no database RLS, the application is the only line of defense — every query is isolated by `TenantContext.currentUserId()` derived from the JWT. A missing `user_id` filter is a data leak.
- **Custom JWT** — short-lived access token (HS256) + opaque refresh token stored hashed in the DB with rotation. BCrypt for passwords. Signing key from environment only.
- **Server is the calculation SSOT** — fees, deposit-due dates, and totals are computed server-side; the client only displays.
- Input validation at the boundary (Jakarta Bean Validation, UUID checks), parameter-bound queries (including native SQL), CORS origin allowlist, and generic error responses (details go only to Discord).

See [docs/PATTERNS.md](docs/PATTERNS.md) and [CLAUDE.md](CLAUDE.md) for the full checklist.

## Environment

All secrets/config are injected via environment variables (`application.yml` references `${ENV}` only); unset values fall back gracefully in local.

| Variable | Purpose | If unset |
|----------|---------|----------|
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | PostgreSQL connection | local default |
| `JWT_SECRET` | JWT signing key (required in prod, ≥32 bytes) | local-only default ⚠️ non-local profiles refuse to boot |
| `JWT_ACCESS_TTL` / `JWT_REFRESH_TTL` | Token expiry (seconds) | 900 / 1209600 |
| `AWS_REGION` / `S3_BUCKET` / `CLOUDFRONT_DOMAIN` | S3 presigned upload | not issued (error on presign) |
| `FCM_ENABLED` / `FCM_CREDENTIALS` | FCM push | logging fallback (no-op) |
| `DISCORD_WEBHOOK_URL` | Ops error alerts | console logging |
| `INTERNAL_API_KEY` | Internal ingest API auth | internal API fully blocked |
| `CORS_ALLOWED_ORIGINS` | App/web origin allowlist (comma-separated) | localhost:3000,8081 |
| `SPRING_PROFILES_ACTIVE` | Active profile | `local` |

> **Production checklist**: set a strong `JWT_SECRET` (non-local profiles fail to boot without it), disable Swagger (`springdoc.swagger-ui.enabled=false`), and consider rate limiting.

> Infrastructure (RDS / S3 / CloudFront / ECR / deployment) is provisioned separately and out of scope for this repo.
