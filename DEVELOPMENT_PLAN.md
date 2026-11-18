# Development Plan

| Phase | Scope | Status |
|---|---|---|
| 1 | Requirements review, architecture, ERD, API contract, UI flows, security design | Delivered |
| 2 | Repository setup, Spring Boot, PostgreSQL, Flyway, local run config | **Delivered** (backend + React/Vite scaffold) |
| 3 | Registration, email verification, login/refresh/logout, password reset, rate limiting | **Delivered** (backend + auth pages) |
| 4 | User & membership management, admin bootstrap, `/users/me` | **Delivered** (backend + member profile + admin users/memberships UI) |
| 5 | Categories, book metadata, upload API, `FileStorageService` + local impl, file validation | **Delivered** (backend + admin books/categories/upload UI) |
| 6 | Azure Blob implementation, cleanup queue + reconciliation job | **Code delivered, not verified against Azure** |
| 7 | Public catalogue, search, filters, detail, cover streaming | **Delivered** (backend + landing, catalogue, detail pages) |
| 8 | PDF eligibility, access tickets, streaming with Range, download | **Delivered** (backend + PDF.js reader + download) |
| 9 | Reading progress, modification requests, audit logs | **Delivered** (backend + member dashboard, profile, requests) |
| 10 | Admin dashboard & management UI | **Delivered** (all admin pages + `/admin/stats`) |
| 11 | Security review, performance, accessibility, E2E | **Delivered** (see `docs/SECURITY_REVIEW.md`) |
| 12 | Azure deployment, CI/CD, monitoring, backups, runbooks | **Delivered as code, not yet deployed** - awaiting approval (needs domains D8 + Azure access) |
| 13 | Mobile number + SMS code (OTP) identity instead of email | **Delivered** (2026-11-18); real SMS needs an MSG91 account + DLT templates |

Phases 3–9 (backend) were built in one step at the user's request (2026-11-16), instead of one
phase at a time.

## Backend delivered (2026-11-16)

45 endpoints in 13 controllers — see `docs/API_SPECIFICATION.md`:

| Area | Controller | Service |
|---|---|---|
| Auth | `AuthController` | `AuthService`, `RefreshTokenService`, `AccessTokenService` |
| Current user | `UserController` | `UserService` |
| Admin users | `AdminUserController` | `AdminUserService`, `AdminBootstrap` |
| Admin memberships | `AdminMembershipController` | `MembershipService` |
| Categories | `CategoryController`, `AdminCategoryController` | `CategoryService` |
| Books | `BookController`, `AdminBookController` | `BookCatalogueService`, `AdminBookService` |
| Reader | `ReaderController`, `ReadingProgressController` | `PdfAccessService`, `AccessTicketService`, `ReadingProgressService` |
| Requests | `ModificationRequestController`, `AdminModificationRequestController` | `ModificationRequestService` |
| Audit | `AdminAuditController` | `AuditService` |

Supporting pieces: JPA entities for all tables, `FileValidator` (magic bytes + PDFBox/ImageIO parsing),
`LocalFileStorageService` / `AzureBlobFileStorageService`, `FileAssetService` (compensating cleanup),
`StorageCleanupJob`, `TokenPurgeJob`, `RateLimiter`, SMTP `AccountEmailService` (after-commit delivery).

### Acceptance results (2026-11-16)

Command: `cd backend && ./mvnw verify` (JDK 26, `--release 21`), PostgreSQL 16.6 embedded.

| Test class | Tests | Result |
|---|---|---|
| AuthFlowTests | 18 | pass |
| LibraryFlowTests | 13 | pass |
| BookAvailabilityTest | 8 | pass |
| MembershipValidityTest | 8 | pass |
| AccessTicketServiceTest | 4 | pass |
| RateLimiterTest | 2 | pass |
| **Total** | **53** | **53 pass, 0 skipped** |

Build log scanned: no tokens, tickets or cookies appear in application logs.

### Known gaps / not verified

- `AzureBlobFileStorageService` compiles but has **not been run against a real storage account**.
- SMTP sending was not exercised against a real server (tests capture emails in memory).
- No automated fault-injection test for "storage upload OK, DB commit fails" (code path exists).
- Rate limiter is per-instance (in memory).
- Refresh-token cookie is `SameSite=Strict`; frontend and API must share a site (same registrable domain), or this needs revisiting when domains are chosen (D8).
- Business decisions D1–D8 (`docs/REQUIREMENTS_REVIEW.md`) are still open; current defaults:
  365-day membership created on verification, admins bypass membership, UTC business timezone.
- Frontend: see the Phase 8 section below.

## Frontend foundation + Phase 8: PDF reader and download (2026-11-16)

React 19 + TypeScript + MUI 9 + Vite 8 SPA in `frontend/`, feature folders per brief §12
(`features/{auth,catalogue,member,reader}`, `services/api`, `contexts`, `routes`, `layouts`, `utils`).

### Phase 8 deliverables

| Item | Where |
|---|---|
| PDF.js reader at `/read/:id` (lazy-loaded chunk) | `features/reader/ReaderPage.tsx` |
| Prev/next, page box + total, zoom in/out, fit width, full screen, search in book with highlights, mobile bottom bar + swipe, keyboard shortcuts | `ReaderPage.tsx`, `PdfPageView.tsx`, `usePdfSearch.ts` |
| Ranged loading with ticket renewal (option A: 60 s tickets unchanged) | `ticketedRangeLoader.ts`, `pdfDocument.ts` |
| Opens at last-read page; saves progress (debounced, flushed on exit) | `ReaderPage.tsx` |
| Eligibility / not-found / "book updated" (`VERSION_CHANGED`) states | `ReaderPage.tsx` |
| Download from book page and reader | `usePdfDownload.ts` |
| Backend: plain ASCII `Content-Disposition` filename (was `=?UTF-8?Q?...?=`) | `ContentDispositionUtils.java` + test |

### Results

| Check | Command | Result |
|---|---|---|
| Backend tests | `cd backend && mvnw verify` (JDK 25) | 55 pass, 0 fail (53 existing + 2 new) |
| Frontend typecheck | `cd frontend && npx tsc -b` | clean |
| Frontend unit tests | `cd frontend && npm test` | 27 pass (4 files) |
| Frontend build | `npx vite build` | OK; reader chunk 502 kB + PDF.js worker, main bundle 601 kB |
| Reader HTTP contract E2E (through Vite proxy) | scripted against local servers | 10/10: range probe 206 + total size, `no-store`, progress round-trip, download filename, ticket rejected after 65 s with `INVALID_TICKET`, renewed ticket works, guest 401 |

### Known issues / not yet verified

- Visual check of the reader in a real browser has to be done manually (no browser automation run).
- Sample PDFs are ~16 KB, smaller than one 64 KB range chunk, so in the browser they load in a
  single request; ticket renewal mid-book is covered by unit tests and the HTTP E2E check, not by
  a large real PDF.
- Search highlights whole text runs that contain the query, not the exact characters.
- Main bundle still > 500 kB (MUI); split further when admin pages are added (Phase 10).

## Phase 9: member pages (2026-11-16)

| Item | Where |
|---|---|
| Dashboard `/me`: membership + account cards, "Continue reading" (latest book, resumes at last page), paged reading history, quick links | `features/member/MePage.tsx` |
| Profile `/me/profile`: edit name; change password (signs out everywhere, then login page shows a notice) | `features/member/ProfilePage.tsx`, `features/auth/LoginPage.tsx` |
| My requests `/me/requests`: paged list with status, type, book link, admin note; "New request" dialog with type, optional book search, description (5000 max); `?new=1&bookId=` pre-fills | `features/member/RequestsPage.tsx` |
| "Suggest a correction" link on the book page; Profile / My requests in the account menu | `BookDetailPage.tsx`, `MainLayout.tsx` |
| Member routes lazy-loaded: main bundle 680 kB -> 327 kB (size warning gone) | `app/App.tsx` |

No backend changes were needed.

### Results

| Check | Result |
|---|---|
| Frontend typecheck / unit tests / build | clean / 27 pass / OK |
| E2E through the Vite proxy (scripted) | 19/19: name update + validation, `roles` ignored on profile update, requests with and without a book, empty description rejected, list newest first, history newest first with title/cover, wrong current password, password = email rejected, change revokes the old refresh token, old password fails, new works, SPA deep links |

### Known issues

- No component tests yet (React Testing Library + MSW still to set up).
- The E2E run left one throwaway member (`e2e-member-…@songbook.local`) and two sample requests in the local dev database.
- Visual check in a browser is manual.

## Phase 10: admin dashboard and management UI (2026-11-16)

Admin area at `/admin/*`, lazy-loaded behind `RequireAdmin` (own 112 kB chunk; main bundle unchanged at 327 kB).

| Page | Features |
|---|---|
| Dashboard `/admin` | Users, valid memberships, published books, pending requests; books by status; accounts by status. Cards link to filtered lists |
| Users `/admin/users`, `/admin/users/:id` | Search + status/role filters; create user (optionally pre-verified, optionally admin); edit name, status, admin role; membership history with add/edit |
| Memberships `/admin/memberships` | Filter by user and status; create/edit dialog (warns that a new ACTIVE record replaces the current one) |
| Books `/admin/books`, `/new`, `/:id` | Search + status/category filters; create with PDF + optional cover and upload progress; edit metadata; replace cover; version history with download of any version; upload new version with change note; archive with confirmation |
| Categories `/admin/categories` | Add, inline edit, activate/deactivate |
| Requests `/admin/requests` | Pending/approved/rejected/all tabs; approve or reject with a note to the member |
| Audit log `/admin/audit` | Filter by action, entity type, entity ID, actor, date range; expandable details |

Shared: URL-backed filters and paging (`useUrlFilters`), friendly messages for `CONCURRENT_MODIFICATION`, `LAST_ADMIN`, `FILE_TOO_LARGE`; client-side file checks mirroring backend limits; XHR upload helper with progress and token refresh.

Backend: new `GET /api/v1/admin/stats` (`admin/AdminStatsController`, `AdminStatsService`, grouped JPQL counts) + `AdminStatsTests`.

### Results

| Check | Command | Result |
|---|---|---|
| Backend tests (clean build) | `mvnw clean verify` (JDK 25) | 57 pass, 0 fail, 0 skipped |
| Frontend | `tsc -b`, `npm test`, `vite build` | clean, 32 pass (5 files), OK |
| Admin E2E through the Vite proxy (scripted) | live servers | 46/46: user CRUD + duplicate email + stale version 409 + LAST_ADMIN; membership supersede + date range + filters; category duplicate + deactivate hides it publicly; book create with PDF+cover, disguised PDF rejected, edit, cover replace, v2 upload with note, historical download (401 without token), archive blocks edits; request approve + ALREADY_REVIEWED + member sees note; audit entries for every book action, no secrets in details; stats deltas; member 403 / guest 401; SPA deep links |

### Known issues

- Public cover responses are cached for 24 h (`Cache-Control: public, max-age=86400`), so after replacing a cover, the public catalogue may show the old image for up to a day. The admin page cache-busts its own preview. Fix option: version the cover URL (e.g. `?v=<updatedAt>`) in `BookDtos.coverUrlOf`.
- E2E left test data tagged "E2E ..." in the local DB (one user, one archived book, one inactive category).
- No component tests (React Testing Library + MSW) and no visual browser check yet.

## Phase 11: security review, accessibility, performance, browser E2E (2026-11-17)

Full report: `docs/SECURITY_REVIEW.md`. Summary:

- **Fixed:** client-abort log flooding (F1); versioned cover URLs (F2); PDF.js runtime assets for scanned
  books, without the scripting engine (F3); reader no longer waits on slow progress (F4); pooled dev
  DataSource, 30 -> 500-1100 req/s locally (F5); 6 WCAG 2.1 AA violations (F6); `rel="noopener"` (F7).
- **Browser E2E:** Playwright + axe in `frontend/e2e` (`npm run test:e2e`), 30 tests: guest flows,
  reader (render, page turn, zoom, search, progress, download), member and admin pages, axe on every
  screen in light and dark mode.
- **Performance:** NFR-07 passes, worst catalogue p95 85 ms at 20 and 220 ms at 50 concurrent clients.

### Results

| Check | Result |
|---|---|
| Backend `mvnw clean verify` (JDK 25) | 57 pass, 0 fail, 0 skipped |
| Frontend `tsc -b` / unit tests / build | clean / 32 pass / OK (PDF.js assets: 200 files, no quickjs) |
| Playwright browser E2E + axe | 30/30 pass |
| `npm audit` | 0 vulnerabilities |
| Load test | 0 errors in 14,600 requests; NFR-07 pass |

### Open items

Backend dependency scanning and the SPA Content-Security-Policy belong to Phase 12 (CI and domains, D8);
manual screen-reader/zoom testing before release. Details in `docs/SECURITY_REVIEW.md` §5.

## Phase 12: Azure deployment, CI/CD, monitoring, backups, runbooks (2026-11-17)

Everything needed to deploy, validated locally. **Nothing is deployed yet**: that needs an Azure
subscription, a GitHub OIDC identity and the domain names (decision D8 = custom domains).

| Deliverable | Where |
|---|---|
| Infrastructure as code (29 resources): App Service + staging slot, PostgreSQL 16 with PITR backups, private Blob Storage (no keys, versioning, soft delete), Key Vault, Static Web Apps, App Insights (ticket masking), managed identities with least-privilege roles, 5 metric alerts, custom domains + managed certificates | `infra/main.bicep`, `infra/modules/sni-binding.bicep`, `infra/env/*.bicepparam` |
| CI: backend verify, frontend checks + audit, Bicep build, Playwright on dev server and on the production build in production topology | `.github/workflows/ci.yml` |
| CD: dev → approval → prod via staging slot + swap, OIDC only, smoke test after each deployment | `.github/workflows/deploy.yml`, `scripts/smoke-test.mjs` |
| Infrastructure workflow with what-if preview and approval | `.github/workflows/infra.yml` |
| Dependency updates | `.github/dependabot.yml` |
| SPA security headers + CSP generated per environment | `scripts/write-swa-config.mjs` |
| Runbook: setup, DNS, secrets + rotation, first admin, migrations, rollback, backup/restore, incidents, hardening | `docs/RUNBOOK.md`; `docs/AZURE_DEPLOYMENT.md` rewritten |

Fixes found while validating:
- **Production bug:** `VITE_API_BASE_URL` was ignored in builds (`import.meta.env?.X` is not substituted by
  Vite), so the deployed SPA would have called its own host for every API request. Fixed in `client.ts`;
  caught by the new cross-origin test mode, which builds with an API origin and serves without a proxy.
- `backend/mvnw` was not executable in git (CI on Linux would fail); mode set to 100755.
- Production SMTP now requires STARTTLS (Azure Communication Services relay).

### Results

| Check | Result |
|---|---|
| Bicep CLI 0.49.2 (Microsoft-signed) | `main.bicep` and both parameter files compile, 0 errors, 0 linter warnings |
| actionlint 1.7.12 (checksum verified) | 3 workflows, 0 issues (shell snippets not checked: no shellcheck) |
| Backend `mvnw clean verify` (JDK 25) | 57 pass |
| Frontend `tsc -b` / unit tests / build / `npm audit --omit=dev` | clean / 32 pass / OK / 0 vulnerabilities |
| Playwright on production build + CSP, same origin | 31/31, 0 CSP violations (self-test proves violations are detected) |
| Playwright on production build, cross-origin (production topology) | 31/31, 0 CSP violations |
| Smoke test against the local production build | 8/9; the expected failure is "API docs disabled" (local profile keeps Swagger on) |

### Not verified (needs a real Azure deployment)

Resource provisioning itself (what-if/apply), managed certificates, Key Vault references resolving,
App Insights URL masking, Azure Blob storage (`AzureBlobFileStorageService` never run against Azure),
ACS SMTP sending, staging-slot swap, OIDC login. Follow `docs/RUNBOOK.md` §1–4 for the first deployment.

## Phase 13: Mobile number + SMS codes replace email identity (2026-11-18)

Why: on the first Azure deployment, verification emails could not be sent (no Azure Communication
Services), so new members could not sign in. The owner chose the mobile number as the identity.

Decisions: sign in with **mobile number + password**; the number is confirmed by a **6-digit SMS code** at
sign-up, and codes are also used for password reset and for members changing their own number (code goes to
the new number). Provider **MSG91** (Indian DLT rules apply) behind `SmsSender`; a `log` provider writes codes
to the log for local/dev and refuses to start unless explicitly allowed. **Email is optional contact data**
and nothing is emailed any more (SMTP/ACS, Mailpit and `spring-boot-starter-mail` removed). Existing
email-only admins get a mobile through `SONGBOOK_BOOTSTRAP_ADMIN_MOBILE` (RUNBOOK §4).

Changes:
- Flyway `V2__mobile_identity.sql`: `users.mobile` (E.164, unique, checked), `mobile_verified(_at)`, `email`
  nullable; new `otp_codes` table (hash only); `user_tokens` dropped.
- API: `/auth/register`, `/auth/verify-mobile`, `/auth/resend-verification`, `/auth/login`,
  `/auth/forgot-password`, `/auth/reset-password` take `mobile`; `/users/me/mobile` + `/mobile/confirm`;
  admin create/update take `mobile`, `mobileVerified`, optional `email`; user search matches mobile.
- SPA: register, code entry (resend countdown), login, two-step forgot password, profile (email + mobile
  change dialog), admin users/memberships show mobile numbers.
- Infra: `MAIL_*` settings removed; `SONGBOOK_SMS_*` / `MSG91_*` settings, `msg91-auth-key` secret
  (prod), `smsProvider` / template / daily-limit parameters.

| Check | Result |
|---|---|
| Backend `mvnw verify` | 95 pass (new: OTP expiry/burn/purpose/reuse, number normalisation, mobile change, cooldown, daily limit, MSG91 request format, provider safety) |
| Frontend `tsc -b` / unit tests | clean / 47 pass |
| Playwright (local dev, existing database) | 30 pass, 1 skipped (prod-build-only test) |
| Upgrade on existing local data | V2 applied; email-only dev admin got its mobile attached at startup |
| Live API flow (local) | register → code from log → verify → login with a differently typed number |
| Bicep | builds; dev and prod parameter files compile |

Not verified: real SMS delivery through MSG91 (needs account, DLT registration and template ids).
