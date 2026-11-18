# Operations Runbook

Covers the Azure deployment defined in `infra/` and `.github/workflows/`. Topology (decision D8):
SPA on `https://<frontend-domain>` (Static Web Apps), API on `https://<api-domain>` (App Service);
both hostnames share one registrable domain so the `SameSite=Strict` refresh cookie works.

Commands use the Azure CLI (`az`) and assume `RG=rg-songbook-<env>`.

## 1. First-time setup (once per environment)

1. **Resource group:** `az group create -n $RG -l <region>`.
2. **Deployment identity (GitHub OIDC, no stored credentials):**
   - Create an Entra app registration / service principal, e.g. `gh-songbook-<env>`.
   - Add federated credentials: subject `repo:<owner>/<repo>:environment:<env>`
     (issuer `https://token.actions.githubusercontent.com`, audience `api://AzureADTokenExchange`).
   - Grant it **Owner** on `$RG` (or Contributor + Role Based Access Control Administrator): the template
     creates role assignments for the app's managed identities.
3. **GitHub environments** `dev` and `prod` (Settings → Environments):
   - Variables: `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID`, `AZURE_RESOURCE_GROUP`,
     `WEBAPP_NAME`, `SWA_NAME`, `API_ORIGIN`, `FRONTEND_ORIGIN` (the last four come from the infra outputs).
   - Secret: `PG_ADMIN_PASSWORD` (generate: `openssl rand -base64 32`).
   - `prod`: add required reviewers (this is the manual approval gate for infra and deployments).
   - When prod is ready, add the **repository** variable `PROD_ENABLED` = `true` (Settings → Secrets and
     variables → Actions → Variables); without it the Deploy workflow skips prod.
4. **Parameters:** replace every `TODO` in `infra/env/<env>.bicepparam` (domains D8, alert email,
   SMS provider and MSG91 template ids, timezone D5, membership days D1).
5. **SMS (sign-up codes, password reset, mobile change):** accounts sign in with a mobile number confirmed by a
   6-digit SMS code.
   - `smsProvider = 'log'` (dev default): codes are **not sent**, they are written to the app log
     (Application Insights → Logs: `AppTraces | where Message has "DEV SMS"`). Never use it in production.
   - `smsProvider = 'msg91'` (prod): create an MSG91 account and complete **DLT registration** (Indian telecom
     rule: registered entity, sender ID, and approved templates). Register three OTP templates — sign-up,
     password reset, mobile change — each containing the `##otp##` variable, and put their template ids in
     the parameter file. The MSG91 auth key goes to Key Vault in §3. Startup fails if any of them is missing.
   - `smsDailyLimit` caps codes per 24 h across all users (cost guard; requests beyond it get HTTP 429).
6. Run the **Infrastructure** workflow for the environment; review the what-if output, approve, then copy
   the outputs into the GitHub environment variables.

## 2. DNS and custom domains

After the first infrastructure run (outputs give the default hostnames and `customDomainVerificationId`):

| Record | Name | Value |
|---|---|---|
| CNAME | `<api-domain>` (e.g. `api.songbook`) | `<apiDefaultHostname>` (`app-…azurewebsites.net`) |
| TXT | `asuid.<api-domain>` | `<customDomainVerificationId>` |
| CNAME | `<frontend-domain>` (e.g. `songbook`) | `<frontendDefaultHostname>` (`…azurestaticapps.net`) |

When the records resolve (`nslookup`), set `bindCustomDomains = true` in the parameter file and run the
Infrastructure workflow again. It binds both hostnames and issues free managed TLS certificates
(auto-renewed). Then set `API_ORIGIN` / `FRONTEND_ORIGIN` to the custom `https://` origins.

Until the custom domains are bound, sign-in works but a page reload logs the user out (cross-site cookie).

## 3. Secrets (Key Vault)

The template stores only `db-password`. Set the others once (you need "Key Vault Secrets Officer"):

```bash
KV=<keyVaultName output>
az keyvault secret set --vault-name $KV -n jwt-secret    --value "$(openssl rand -base64 48)"
az keyvault secret set --vault-name $KV -n ticket-secret --value "$(openssl rand -base64 48)"
# Only with smsProvider = 'msg91':
az keyvault secret set --vault-name $KV -n msg91-auth-key --value '<MSG91 auth key>'
az webapp restart -g $RG -n <webAppName>
```

Check: Portal → App Service → Environment variables → each Key Vault reference shows a green tick.
A red reference means the app cannot start (`*_SECRET must be set`).

**Rotation:**

| Secret | Effect of rotating | Procedure |
|---|---|---|
| `jwt-secret` | All access tokens invalid; users silently refresh (refresh tokens are stored hashed in the DB, not signed) | Set new value, restart |
| `ticket-secret` | Open readers renew their 60 s ticket automatically | Set new value, restart |
| `db-password` | — | `az postgres flexible-server update -g $RG -n <server> -p '<new>'`, set the secret, restart |
| `msg91-auth-key` | — | Create a new key in MSG91, set the secret, restart, then delete the old key in MSG91 |

## 4. First deployment and the first administrator

1. Push to `main` (or run the **Deploy** workflow). Flyway creates the schema on first start.
2. Create the first admin by setting **temporary** app settings (never stored in Key Vault or the repo):
   ```bash
   az webapp config appsettings set -g $RG -n <webAppName> --settings \
     SONGBOOK_BOOTSTRAP_ADMIN_MOBILE=+91XXXXXXXXXX SONGBOOK_BOOTSTRAP_ADMIN_EMAIL=<email> \
     SONGBOOK_BOOTSTRAP_ADMIN_PASSWORD='<strong password>'
   # wait for the restart, sign in once with the mobile number, then remove them:
   az webapp config appsettings delete -g $RG -n <webAppName> --setting-names \
     SONGBOOK_BOOTSTRAP_ADMIN_MOBILE SONGBOOK_BOOTSTRAP_ADMIN_EMAIL SONGBOOK_BOOTSTRAP_ADMIN_PASSWORD
   ```
   The bootstrap only creates an admin while none exists (`AdminBootstrap`); it is audited as `ADMIN_BOOTSTRAPPED`.
   The mobile is pre-verified (no SMS needed); the email is optional contact data.

   **Admins created before sign-in moved to mobile numbers** have no mobile and cannot sign in. Set
   `SONGBOOK_BOOTSTRAP_ADMIN_EMAIL` (their email) and `SONGBOOK_BOOTSTRAP_ADMIN_MOBILE` only (no password): on
   startup the number is attached, pre-verified, to that admin (audited as `MOBILE_CHANGED`). Remove both
   settings afterwards. Other existing accounts get a mobile from an admin on the user's page.
3. Sign in, change the password in **Profile**, create categories and upload books in **Admin**.

## 5. Routine deployments and database migrations

- Push to `dev` → **Deploy**: tests → dev → smoke test (dev only).
- Merge to `main` → **Deploy**: tests → dev → smoke test → (approval) → prod staging slot → readiness →
  swap → SPA → smoke test. The prod job runs only when the repository variable `PROD_ENABLED` is `true`;
  until then it shows as skipped.
- Manual smoke test any time: `node scripts/smoke-test.mjs https://<frontend> https://<api>`.
- **Migrations must be backward compatible.** The staging slot starts against the *production* database,
  so Flyway runs before the swap while the old version still serves traffic. Use expand/contract:
  add columns/tables first (nullable or with defaults), deploy code that uses them, and drop old columns
  in a *later* release. Never rename or drop in the same release that stops using them.

## 6. Rollback

| What | How | Time |
|---|---|---|
| API (prod) | `az webapp deployment slot swap -g $RG -n <webAppName> --slot staging --target-slot production` (the previous version is still in the slot) | ~1 min |
| API (any env) | Re-run **Deploy** on the previous good commit, or redeploy its `api-jar` artifact (kept 30 days) | ~5 min |
| SPA | Re-run **Deploy** on the previous commit | ~3 min |
| Database | Only if a migration damaged data: restore (§7). Prefer a forward fix migration. | |

A schema change cannot be rolled back by swapping. This is why migrations must stay backward compatible.

## 7. Backups and restore

| Data | Protection | Restore |
|---|---|---|
| PostgreSQL | Automatic backups with point-in-time restore: 7 days (dev), 35 days + geo-redundant (prod) | `az postgres flexible-server restore -g $RG -n <new-server> --source-server <server> --restore-time <UTC ISO time>`, then point `DB_URL` at the new server (update the app setting, or re-run infra with the new name) and restart |
| Blobs (PDFs, covers) | Versioning + 14-day soft delete for blobs and containers | Portal → container → "Show deleted blobs" → Undelete, or promote a previous version |
| Key Vault | Soft delete 90 days (+ purge protection in prod) | `az keyvault secret recover` |

Test a restore at least once per quarter: restore to a new server, start a dev App Service against it,
check the catalogue, then delete the copy.

## 8. Monitoring and incidents

Alerts (email to `alertEmail`): API 5xx > 10 in 5 min, average response time > 1 s, health check < 100 %,
PostgreSQL CPU > 80 %, storage > 85 %.

First checks:
1. Portal → App Service → **Health check** and **Log stream**.
2. Application Insights → Failures / Performance. Useful queries (Logs):
   ```kusto
   requests | where timestamp > ago(1h) | summarize count(), percentile(duration, 95) by name, resultCode
   exceptions | where timestamp > ago(1h) | summarize count() by type, outerMessage
   ```
   Request URLs show `ticket=***` (masked by the agent configuration in `infra/main.bicep`).
3. Security events (in the app database, not telemetry): **Admin → Audit log**, filter `LOGIN_FAILED`,
   `ACCOUNT_TEMPORARILY_LOCKED`, `REFRESH_TOKEN_REUSE`.
4. Storage errors (`STORAGE_UNAVAILABLE`): check the managed identity's role assignments on the containers.

## 9. Hardening backlog and scaling notes

- **Private networking (prod):** VNet-integrate App Service and give PostgreSQL private access
  (instead of the "Allow Azure services" firewall rule). Same for Storage and Key Vault (private endpoints).
- **Database identity:** the app uses the server admin login. Create a dedicated role owning only the
  `songbook` schema, or switch to Entra (managed identity) authentication.
- **Scaling out:** the login rate limiter is per instance (move to Redis or Front Door WAF), and the
  scheduled jobs (`TokenPurgeJob`, `StorageCleanupJob`) run on every instance. Set
  `SONGBOOK_JOBS_ENABLED=false` on all but one, or add a distributed lock.
- **Supply chain:** pin GitHub Actions to commit SHAs; enable Dependabot security updates and, if available,
  code scanning.
