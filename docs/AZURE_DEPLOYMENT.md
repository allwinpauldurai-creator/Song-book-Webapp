# Azure Deployment

> No real resource IDs, domains or credentials are recorded here. Fill in the placeholders when the
> subscription and domains are confirmed (decision D8).

## 1. Resources (per environment: `dev`, `prod`)

| Resource | Name (example) | SKU guidance | Notes |
|---|---|---|---|
| Resource group | `rg-songbook-<env>` | | One per environment |
| App Service plan (Linux) | `asp-songbook-<env>` | B1 dev / P1v3 prod | |
| App Service | `app-songbook-api-<env>` | Java 21 SE or container | HTTPS Only, system-assigned Managed Identity, health check `/actuator/health/readiness` |
| Static Web App | `swa-songbook-ui-<env>` | Standard (custom domain + SLA) | React build |
| PostgreSQL Flexible Server | `pg-songbook-<env>` | Burstable B1ms dev / General Purpose prod, PG 16 | Entra auth enabled, firewall/VNet restricted, backups 7 d dev / 35 d prod, geo-redundant prod optional |
| Storage account | `stsongbook<env>` | StorageV2, LRS/ZRS | Public blob access **disabled**, shared-key access disabled, containers `book-covers`, `book-pdfs`, soft delete + versioning on |
| Key Vault | `kv-songbook-<env>` | Standard, RBAC mode | `jwt-secret`, `ticket-secret`, DB password (if not Entra), email connection |
| Application Insights | `appi-songbook-<env>` | Workspace-based | Java agent auto-instrumentation |
| Communication Services Email | `acs-songbook-<env>` | | Pending decision D4 |

## 2. Identity / RBAC

App Service Managed Identity gets:
- `Storage Blob Data Contributor` on the storage account (scope: the two containers).
- `Key Vault Secrets User` on the Key Vault.
- PostgreSQL: Entra admin creates a DB role for the identity (preferred) or a password stored in Key Vault.

## 3. App settings (App Service)

| Setting | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` / `dev` |
| `DB_URL` | `jdbc:postgresql://<server>.postgres.database.azure.com:5432/songbook?sslmode=require` |
| `DB_USERNAME` / `DB_PASSWORD` | Key Vault references |
| `SONGBOOK_CORS_ORIGINS` | `https://<frontend-domain>` |
| `SONGBOOK_TIMEZONE` | decision D5 |
| `SONGBOOK_MEMBERSHIP_DAYS` | decision D1 |
| `SONGBOOK_STORAGE_PROVIDER` | `azure` |
| `AZURE_STORAGE_ACCOUNT_ENDPOINT` | `https://<account>.blob.core.windows.net` (Phase 6) |

## 4. CI/CD (GitHub Actions, Phase 12)

1. Checkout → 2. `./mvnw verify` (unit + Testcontainers) → 3. frontend `npm ci && npm run lint && npm test && npm run build`
→ 4. build container / jar → 5. deploy to **dev** (OIDC federated credential, no stored secrets)
→ 6. smoke test `/actuator/health` + catalogue → 7. **manual approval** (GitHub environment protection) → 8. deploy to **prod** (slot swap).

## 5. Operations

- Alerts: 5xx rate, p95 latency, App Service health, DB CPU/storage, failed logins spike.
- Backups: PostgreSQL PITR; Blob soft delete (14 d) + versioning.
- Flyway runs on app start; prod migrations reviewed in PR; `clean-disabled: true`.
- Reconciliation job for `storage_cleanup_queue` (Phase 6).
