# Song Book Library

Public song book catalogue with member-only online reading and PDF download, plus an admin console.
Spring Boot 3 (Java 21) API · PostgreSQL · Azure Blob Storage · React/TypeScript/MUI (planned).

Project brief: [`SONG_BOOK_LIBRARY_CLAUDE_CODE_GUIDE.md`](SONG_BOOK_LIBRARY_CLAUDE_CODE_GUIDE.md) ·
Status: [`DEVELOPMENT_PLAN.md`](DEVELOPMENT_PLAN.md) · Design docs: [`docs/`](docs)

## Repository layout

```
backend/            Spring Boot API (Maven)
docs/               Architecture, ERD, API, security, Azure, testing, UI flows
docker-compose.yml  Local PostgreSQL
```

## Prerequisites

- JDK 21+ (the build targets Java 21)
- For running the app locally: PostgreSQL 16 (via `docker compose` or your own install)
- Tests need neither Docker nor a database — they use embedded PostgreSQL

## Run locally without Docker (simplest)

Uses an embedded PostgreSQL (data kept in `backend/data/postgres`), prints SMS codes to the console
(logger `DEV-SMS`) instead of sending them, and creates a dev admin who signs in with mobile
`99999 99999` / `ChangeMe-Admin-123`. Local development only.

```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\openjdk-26.0.2.1"   # any JDK 21+
cd backend
.\mvnw.cmd spring-boot:test-run
```

## Run locally with Docker

```bash
docker compose up -d                    # PostgreSQL :5432
cd backend
# first start: create an admin account (remove these afterwards)
export SONGBOOK_BOOTSTRAP_ADMIN_MOBILE=+919876543210
export SONGBOOK_BOOTSTRAP_ADMIN_PASSWORD='choose-a-long-password'
./mvnw spring-boot:run                  # Windows: mvnw.cmd spring-boot:run  (profile: local)
```

- API docs: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health
- SMS codes (sign-up, password reset, mobile change) are printed to the console (`local` profile).
- Uploaded files are stored under `backend/data/storage`.

In the `local` profile random JWT/ticket keys are generated at startup (sessions end on restart).

## Build and test

```bash
cd backend
./mvnw verify
```

## Configuration

| Env var | Default (local) | Purpose |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` | `local`, `dev`, `prod` |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | local container | Database |
| `SONGBOOK_JWT_SECRET` | generated (local only) | Base64, ≥32 bytes. **Required** in dev/prod |
| `SONGBOOK_TICKET_SECRET` | generated (local only) | Base64, ≥32 bytes, PDF access tickets. **Required** in dev/prod |
| `SONGBOOK_FRONTEND_URL` | `http://localhost:5173` | Frontend origin |
| `SONGBOOK_CORS_ORIGINS` | `http://localhost:5173` | Comma-separated frontend origins |
| `SONGBOOK_TIMEZONE` | `UTC` | Timezone for date rules |
| `SONGBOOK_MEMBERSHIP_DAYS` | `365` (placeholder) | Default membership length, 0 = no end |
| `SONGBOOK_STORAGE_PROVIDER` | `local` | `local` or `azure` |
| `AZURE_STORAGE_ACCOUNT_ENDPOINT` | — | `https://<account>.blob.core.windows.net` (azure provider) |
| `SONGBOOK_SMS_PROVIDER` | `log` | `msg91` (real SMS) or `log` (codes in the log) |
| `SONGBOOK_SMS_ALLOW_LOG` | `false` (`true` in `local`) | Must be `true` for the `log` provider to start |
| `MSG91_AUTH_KEY` / `MSG91_TEMPLATE_VERIFY` / `_RESET` / `_CHANGE` | — | MSG91 key and DLT template ids (`msg91` provider) |
| `SONGBOOK_SMS_DAILY_LIMIT` | `500` | SMS codes per 24 h across all users |
| `SONGBOOK_BOOTSTRAP_ADMIN_MOBILE` / `_EMAIL` / `_PASSWORD` / `_NAME` | — | Creates the first admin if none exists (mobile required; see RUNBOOK §4) |

Generate a secret: `openssl rand -base64 32`. 
