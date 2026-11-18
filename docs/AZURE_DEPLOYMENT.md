# Azure Deployment

Infrastructure as code: `infra/main.bicep` (one environment per resource group) with
`infra/env/dev.bicepparam` and `infra/env/prod.bicepparam`. Operations: `docs/RUNBOOK.md`.
No real resource IDs, domains or credentials are recorded in the repository.

## 1. Topology (decision D8: custom domains)

```
Browser ── https://songbook.example.org ──────► Static Web Apps (SPA, CSP + security headers)
   │
   └──── https://api.songbook.example.org ────► App Service (Spring Boot, Java 21)
                                                   ├─ PostgreSQL Flexible Server 16
                                                   ├─ Blob Storage (Managed Identity, no keys)
                                                   ├─ Key Vault (secret references)
                                                   └─ Application Insights (Java agent)
```

The SPA and API are different **origins** but the same **site** (shared registrable domain), so CORS is
used for the API and the `SameSite=Strict` refresh cookie is still sent. On Azure's default hostnames
(`*.azurestaticapps.net` / `*.azurewebsites.net`) the cookie would not be sent and every page reload would
log the user out. Custom domains are therefore required for real use.

## 2. Resources (`infra/main.bicep`)

| Resource | dev | prod | Notes |
|---|---|---|---|
| App Service plan (Linux) | B1 | P1v3 | |
| App Service | | + `staging` slot | Java 21, HTTPS only, TLS 1.2, FTP/basic publishing disabled, health check `/actuator/health/readiness`, system-assigned identity |
| Static Web Apps | Standard | Standard | Custom domain, headers from `scripts/write-swa-config.mjs` |
| PostgreSQL Flexible Server 16 | B1ms, 7-day backups | D2ds_v5, 35-day geo-redundant backups | TLS required; "Allow Azure services" firewall (private access in the backlog) |
| Storage account | LRS | ZRS | Public blob access off, shared keys off, versioning, 14-day soft delete; containers `book-covers`, `book-pdfs` |
| Key Vault | RBAC | RBAC + purge protection | `db-password` (template); `jwt-secret`, `ticket-secret`, `msg91-auth-key` (RUNBOOK §3) |
| Log Analytics + Application Insights | 30 days | 90 days | Agent masks `ticket=` in request URLs |
| Action group + 5 metric alerts | | | 5xx, response time, health, DB CPU, DB storage |

Role assignments (least privilege): the app's (and slot's) managed identity gets **Storage Blob Data
Contributor** on the two containers only, and **Key Vault Secrets User** on the vault.

## 3. App settings (set by the template)

| Setting | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | server FQDN with `sslmode=require` / admin login / Key Vault ref |
| `SONGBOOK_JWT_SECRET`, `SONGBOOK_TICKET_SECRET` | Key Vault refs |
| `SONGBOOK_FRONTEND_URL`, `SONGBOOK_CORS_ORIGINS` | `https://<frontend domain>` |
| `SONGBOOK_TIMEZONE`, `SONGBOOK_MEMBERSHIP_DAYS` | parameters (D5, D1) |
| `SONGBOOK_STORAGE_PROVIDER`, `AZURE_STORAGE_ACCOUNT_ENDPOINT` | `azure`, blob endpoint |
| `SONGBOOK_SMS_PROVIDER`, `SONGBOOK_SMS_ALLOW_LOG`, `SONGBOOK_SMS_DAILY_LIMIT` | `log` + `true` on dev (codes in App Insights), `msg91` + `false` on prod; parameters `smsProvider`, `smsDailyLimit` |
| `MSG91_AUTH_KEY`, `MSG91_TEMPLATE_OTP` (+ optional `_RESET` / `_CHANGE`) | Key Vault ref, DLT template id parameters (only when `smsProvider = msg91`) |
| `APPLICATIONINSIGHTS_*` | connection string, agent `~3`, URL masking config |

The SPA gets its API origin at build time (`VITE_API_BASE_URL`, set by the deploy workflow).

## 4. CI/CD (`.github/workflows`)

| Workflow | Trigger | Steps |
|---|---|---|
| `ci.yml` | push / PR | backend `mvnw verify`; frontend typecheck, unit tests, build, `npm audit`; Bicep build; Playwright E2E on the dev server **and** on the production build in the production topology (cross-origin API, real CSP) |
| `deploy.yml` | push to `dev` or `main` | tests → jar → **dev** (API, readiness, SPA, smoke test); from `main` only and when the repository variable `PROD_ENABLED` is `true`: → **approval** → **prod** (staging slot, readiness, swap, SPA, smoke test) |
| `infra.yml` | manual | what-if preview → (approval for prod) → apply, outputs in the job summary |
| `dependabot.yml` | weekly | Maven, npm, GitHub Actions updates |

Authentication is GitHub OIDC → Entra ID; no Azure credentials or deployment tokens are stored in GitHub.

## 5. Open decisions affecting deployment

D1 membership days, D5 timezone, D8 domain names, and the MSG91 DLT template ids (prod). All are parameters
(`TODO` markers in `infra/env/*.bicepparam`).
