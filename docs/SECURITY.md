# Security Design

## 1. Identity and roles

- Persisted roles: `MEMBER`, `ADMIN`. Guest = anonymous (no DB role).
- Public registration always assigns `MEMBER` only; request DTOs have no role/status fields.
- Admin role changes go through `/admin/users/{id}`; the service refuses to demote/disable the
  last active admin (row-locked count check inside the transaction).
- First admin is created by `AdminBootstrap` from `SONGBOOK_BOOTSTRAP_ADMIN_EMAIL` / `_PASSWORD` when no admin exists — never via the API. Remove those settings after first start.

## 2. Authentication (Phase 3)

| Item | Design |
|------|--------|
| Password hashing | Delegating encoder, BCrypt (cost 12) default |
| Access token | JWT, HS256 with 256-bit secret from Key Vault (RS256 if another service ever verifies), 15 min, claims `sub`, `roles`, `iss`, `aud`, `iat`, `exp`, `jti`. Held in SPA memory only |
| Refresh token | 256-bit random, SHA-256 hash stored, 14 days, `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`. Rotated every use; presenting a revoked token revokes the whole family |
| CSRF | API is bearer-token based (CSRF disabled for it). The cookie-authenticated `/auth/refresh` and `/auth/logout` additionally require an allowed `Origin` header |
| Logout | Revokes refresh family, clears cookie. Access token expires naturally (≤15 min) |
| Disabled user / role change | Every request re-reads the user from the DB (`DatabaseJwtAuthenticationConverter`), so disabling a user or revoking ADMIN takes effect immediately |
| Email verification / reset | 256-bit random token, SHA-256 stored in `user_tokens`, 24 h / 30 min expiry, single-use (`used_at`) |
| Enumeration | Register/forgot/resend always return 202 with identical body |
| Rate limiting | In-memory sliding window (`RateLimiter`) per IP + per email: login 20/5 min per IP and 10/5 min per email; register 5/10 min per IP; forgot/resend 3/15 min per email. Per-instance only: move to Redis or Front Door WAF if scaled to more than one instance |
| Lockout | 5 consecutive failures → `locked_until = now + 15 min` |

## 3. Authorization

- Request-level rules in `SecurityConfig` (deny by default; `/admin/**` → `ADMIN`).
- Method-level `@PreAuthorize` on services for defence in depth.
- Member endpoints derive the user id from the token, never from the path/body (no IDOR).
- 401 vs 403 via JSON entry point / access-denied handler.

## 4. PDF eligibility (single service method, used by read, download and content)

```
account_status == ACTIVE && (locked_until is null || past)
&& email_verified
&& (isAdmin || exists membership: MembershipValidity.isValid(status, start, end, today))
&& BookAvailability.isAvailable(status, effective, expiry, today)
&& book.current_version_id != null
```

## 5. File access — chosen approach: authenticated streaming

| | Backend streaming (chosen) | User-delegation SAS |
|---|---|---|
| Eligibility checked per request | Yes | Only at issue time |
| URL leakage risk (logs, history, referrer, analytics) | Ticket valid 60 s, bound to user+book | URL usable by anyone until expiry |
| Works with local storage | Yes | No |
| API bandwidth/CPU | Higher | Offloaded to Blob |

Mitigations: HTTP Range (PDF.js loads incrementally), streaming without buffering, App Service
autoscale. Tickets are excluded from access logs (query string scrubbing) and never sent to
Application Insights (telemetry processor strips `ticket`).

Response headers on PDF content: `Content-Type: application/pdf`, `X-Content-Type-Options: nosniff`,
`Cache-Control: private, no-store`, `Content-Disposition` with a plain quoted ASCII filename
(`ContentDispositionUtils.safeBaseName` strips everything else, so no RFC 5987 `filename*` is needed).

**Reader and the 60-second ticket.** PDF.js requests byte ranges for as long as a book is open, but
each ticket expires after 60 s and `/content` re-checks expiry on every request. The SPA therefore
renews the ticket itself (`frontend/src/features/reader/ticketedRangeLoader.ts`): it requests a new
ticket when the current one is 40 s old (local clock, so client clock skew does not matter), and on
an `INVALID_TICKET` response it renews once and retries the range. Renewals call `read-access`, so
eligibility is re-checked each time; a different `versionId` stops the reader with "book updated".
The ticket TTL itself is unchanged.

**Not DRM:** browser rendering cannot prevent screenshots, copying or saving.

## 6. Upload validation

- Size limits (`songbook.storage.max-pdf-bytes`, `max-image-bytes`) + multipart limits.
- PDF: first bytes `%PDF-`, parsed with Apache PDFBox (rejects malformed/encrypted), page count recorded.
- Images: magic bytes for JPEG/PNG/WebP, decoded to verify, dimensions capped.
- Browser content type and filename are ignored for decisions; filename kept only as metadata (sanitized).
- Storage names are generated UUIDs. No HTML/SVG/executables accepted.
- Production: Microsoft Defender for Storage malware scanning recommended.

## 7. Headers, CORS, transport

- HTTPS only (App Service "HTTPS Only", HSTS at the edge).
- `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`.
- CORS: explicit origin allow-list from `SONGBOOK_CORS_ORIGINS`; credentials allowed only for those.
- SPA CSP (Static Web Apps config): `default-src 'self'; connect-src 'self' <api>; img-src 'self' <api> data:; worker-src 'self' blob:`.

## 8. Secrets and logging

- No secrets in repo. Azure: Key Vault references in App Service settings; Managed Identity for Blob/Key Vault.
- Never log: passwords, tokens (JWT, refresh, verify/reset, tickets), SAS, `Authorization`/`Cookie` headers.
- Error responses never include stack traces (`server.error.include-*: never`).

## 9. Threat model (STRIDE summary)

| Threat | Example | Control |
|---|---|---|
| Spoofing | Credential stuffing | Rate limit, lockout, BCrypt |
| Tampering | Mass assignment of `roles` | Explicit DTOs, no role fields on member endpoints |
| Repudiation | Admin denies change | Append-only `audit_logs` |
| Information disclosure | Guessing PDF URL | No public containers; ticket-gated streaming |
| DoS | Huge uploads | Size limits, admin-only upload, App Service limits |
| Elevation | Member hits `/admin/**` | Deny-by-default + role checks at two layers |
