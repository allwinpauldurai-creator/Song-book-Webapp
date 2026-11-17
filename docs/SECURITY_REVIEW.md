# Phase 11 Review: Security, Accessibility, Performance (2026-11-17)

Scope: the code on `feature-songbook` after Phase 10 (backend + React SPA), run locally
(`LocalDevApplication`, embedded PostgreSQL 16, Vite dev server). Azure is not deployed yet (Phase 12).

## 1. Findings and fixes

| # | Area | Finding | Severity | Status |
|---|---|---|---|---|
| F1 | Logging / DoS | A client disconnecting mid-download (cover or PDF range) was logged as ERROR with a full stack trace, then the handler failed again writing JSON into an `image/png` response. Any client could flood logs by cancelling downloads. | Medium | **Fixed**: `GlobalExceptionHandler` handles `AsyncRequestNotUsableException` at DEBUG |
| F2 | Caching | Public covers are cached 24 h (`Cache-Control: public, max-age=86400`) at a fixed URL, so a replaced cover stayed stale in browsers/CDN for a day. | Low | **Fixed**: cover URL carries the file-asset id (`/cover?v=<assetId>`); test asserts the URL changes on replace |
| F3 | Reader robustness | PDF.js 6 needs runtime assets (JBIG2/JPEG 2000 wasm decoders, CMaps, standard fonts). They were not configured, so scanned books would render images blank ("JBig2 failed to initialize"). | Medium (functional) | **Fixed**: `vite-plugin-pdfjs-assets.ts` serves/bundles them; the PDF scripting engine (quickjs) is deliberately **not** shipped. Browser test checks the allow-list |
| F4 | Reader UX | The reader waited for saved progress *and* the PDF (`Promise.all`); a slow progress call delayed the book (seen: 6 s under load). | Low | **Fixed**: progress waits at most 1.5 s, applied later only if the reader has not moved |
| F5 | Performance (dev only) | `LocalDevApplication` used an unpooled `PGSimpleDataSource`: a new PostgreSQL connection (process on Windows) per query capped the local API at ~30 req/s. Production uses Hikari and was not affected. | Low (dev) | **Fixed**: Hikari pool (10) in the dev launcher |
| F6 | Accessibility | 6 WCAG 2.1 AA violations found by axe: secondary teal, warning orange and info blue below 4.5:1 with white chip text; reader scroll area not keyboard-reachable; admin nav `<a>` directly inside `<ul>`; disabled-field helper text below contrast. | Serious (axe) | **Fixed**: theme colours (teal 700 `#00796b` 5.3:1, warning `#b45309` 5.0:1, info `#0277bd` 4.8:1); focusable labelled reader region; nav wrapped in `<li>`; profile email read-only instead of disabled |
| F7 | Hardening | "Public page" link opened a new tab without `rel`. | Info | **Fixed**: `rel="noopener"` |

## 2. Checks that passed (no change needed)

| Check | Evidence |
|---|---|
| No secrets in logs | Backend logs of all E2E runs (Phases 8–10, ~300 requests incl. login, password change, tickets, uploads) scanned for `ticket=`, `Bearer`, JWTs (`eyJhbGci`), `sb_refresh=`, test passwords: **0 hits** |
| No secrets in audit details | Phase 10 E2E asserts audit JSON contains no passwords, tokens or tickets |
| Deny-by-default authorization | `SecurityConfig`: explicit `permitAll` list, `/admin/**` → ADMIN, `anyRequest().authenticated()`; E2E: member 403 / guest 401 on admin APIs, historical PDF download 401 without token |
| Mass assignment | `PUT /users/me` with extra `roles: ["ADMIN"]` ignored (Phase 9 E2E) |
| Security headers | `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer` set in `SecurityConfig`; PDFs `Cache-Control: private, no-store` (E2E) |
| Production config | `application-prod.yml`: Swagger/OpenAPI disabled, `refresh-cookie-secure: true`, generated secrets disabled, Flyway `clean` disabled, every secret required at startup |
| Uploads | Disguised PDF rejected `INVALID_FILE` (E2E); size/type checked client-side and server-side |
| Concurrency | Stale `version` → 409 `CONCURRENT_MODIFICATION`; last admin → 409 `LAST_ADMIN` (E2E) |
| Frontend XSS / token storage | No `dangerouslySetInnerHTML`, `innerHTML`, `eval`, `new Function`; no `localStorage`/`sessionStorage`/`document.cookie`; access token in memory only; `?next=` restricted to same-site paths (unit-tested) |
| Dependencies (frontend) | `npm audit`: 0 vulnerabilities |
| Refresh-token CSRF | `Origin` checked when present; browsers always send it on cross-site POST, and the cookie is `SameSite=Strict` |

## 3. Accessibility (WCAG 2.1 A/AA, axe-core via Playwright)

Every screen, **light and dark mode**: landing, catalogue, book detail, login, register, forgot
password, reader (with PDF loaded), dashboard, profile, my requests + new-request dialog, admin
dashboard, users, user detail + membership dialog, memberships, books, book detail/versions,
new book, categories, requests, audit log → **0 violations**. Regression-tested in `frontend/e2e`.

axe covers roughly half of WCAG; not covered by automation: screen-reader walkthrough (NVDA/VoiceOver),
200 % zoom/reflow, focus order review. Recommended before release.

## 4. Performance (NFR-07: catalogue p95 < 500 ms)

Node load script against the API directly (`localhost:8080`, embedded PostgreSQL, 10 sample books,
Windows laptop), after F5:

| Endpoint | 20 concurrent: req/s · p95 | 50 concurrent: req/s · p95 |
|---|---|---|
| `GET /books?size=20` | 564 · 77 ms | 475 · 220 ms |
| `GET /books?q=hymn` | 551 · 81 ms | 704 · 120 ms |
| `GET /books?categoryId=` | 501 · 85 ms | 905 · 90 ms |
| `GET /books/{id}` | 603 · 80 ms | 1119 · 64 ms |
| `GET /categories` | 818 · 49 ms | 1156 · 71 ms |
| `GET /books/{id}/cover` | 514 · 82 ms | 885 · 85 ms |
| `GET /users/me` (auth) | 482 · 85 ms | 683 · 106 ms |
| `GET read-access` (auth, audited) | 398 · 88 ms | 604 · 131 ms |
| `GET content` 64 KB range | 534 · 57 ms | 729 · 100 ms |

0 errors in 14,600 requests. **NFR-07 passes** (worst catalogue p95 220 ms at 50 concurrent).
Caveat: tiny dataset, local machine. Re-run against Azure with production-sized data in Phase 12.

## 5. Open items (not fixed in Phase 11)

| Item | Why open | Suggested phase |
|---|---|---|
| Backend dependency vulnerability scan | **Done in Phase 12:** Dependabot (Maven, npm, Actions) in `.github/dependabot.yml`; enable Dependabot security alerts in the repository settings | — |
| SPA Content-Security-Policy | **Done in Phase 12:** generated per environment by `scripts/write-swa-config.mjs`; browser suite passes on the production build with 0 CSP violations, same-origin and cross-origin | — |
| Rate limiter is per-instance | In-memory by design (SECURITY.md §2) | When scaling out |
| `TestInfrastructure` (integration tests) still uses an unpooled DataSource | Only slows tests slightly | Optional |
| Manual screen-reader and zoom testing | Not automatable | Before release |
