# API Specification (v1)

Base path `/api/v1`. JSON unless noted. Live OpenAPI: `/swagger-ui.html` (disabled in prod).
**All endpoints below are implemented** (backend) and covered by integration tests in `backend/src/test`.

## Conventions

- Auth: `Authorization: Bearer <access JWT>`. Refresh token in `sb_refresh` HttpOnly cookie (path `/api/v1/auth`).
- Pagination: `?page=0&size=20&sort=title,asc` (max size 100) → `PageResponse { content, page, size, totalElements, totalPages }`.
- Errors: `ApiError { timestamp, status, code, message, path, fieldErrors[] }`.
  Codes: `VALIDATION_FAILED`, `MALFORMED_REQUEST`, `INVALID_SORT`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`,
  `CONCURRENT_MODIFICATION`, `FILE_TOO_LARGE` (413), `INVALID_FILE`, `INVALID_DATE_RANGE`, `INVALID_CODE`, `INVALID_MOBILE`, `INVALID_CREDENTIALS`,
  `ACCOUNT_LOCKED`, `ACCOUNT_DISABLED`, `MOBILE_NOT_VERIFIED`, `MOBILE_IN_USE`, `SAME_MOBILE`, `INVALID_REFRESH_TOKEN`, `INVALID_ORIGIN`, `LAST_ADMIN`, `EMAIL_IN_USE`,
  `CATEGORY_EXISTS`, `ACTIVE_MEMBERSHIP_EXISTS`, `ALREADY_REVIEWED`, `BOOK_ARCHIVED`, `INVALID_TICKET`, `VERSION_CHANGED`,
  `RATE_LIMITED` (429), `STORAGE_UNAVAILABLE` (503), `INTERNAL_ERROR`.
- 401 = not authenticated; 403 = authenticated but not allowed. PDF eligibility failures are 403 with code `ACCOUNT_INACTIVE`, `MOBILE_NOT_VERIFIED` or `MEMBERSHIP_INVALID`; an unavailable book is 404.
- Updates to versioned resources send `version`; mismatch → 409.

## Auth

| Method | Path | Access | Request → Response |
|---|---|---|---|
| POST | `/auth/register` | Public | `{fullName, mobile, email?, password}` → 202 (generic message, even if the number exists); sends an SMS code |
| POST | `/auth/verify-mobile` | Public | `{mobile, code}` → 204, creates the default membership; 400 `INVALID_CODE` |
| POST | `/auth/resend-verification` | Public | `{mobile}` → 202 generic |
| POST | `/auth/login` | Public | `{mobile, password}` → 200 `{accessToken, expiresIn, user}` + Set-Cookie refresh |
| POST | `/auth/refresh` | Refresh cookie | → 200 `{accessToken, expiresIn}` + rotated cookie |
| POST | `/auth/logout` | Refresh cookie (works after the access token expired) | → 204, revokes refresh family, clears cookie |
| POST | `/auth/forgot-password` | Public | `{mobile}` → 202 generic; sends an SMS code |
| POST | `/auth/reset-password` | Public | `{mobile, code, newPassword}` → 204, revokes all sessions |

Mobile numbers are accepted loosely (`98765 43210`, `+91 98765-43210`, `09876543210`) and stored as E.164
(`+919876543210`); a 10-digit Indian number gets `+91`, other countries need `+<code>`. SMS codes: 6 digits, 10 min,
single-use, 5 wrong guesses burn the code, 60 s between codes (see `docs/SECURITY.md` §2).

Password policy: 10–128 chars, not equal to the mobile number; checked server-side.

## Current user

| GET | `/users/me` | Member/Admin | → `{id, fullName, mobile, mobileVerified, email?, roles, membership{status,startDate,endDate,valid}}` |
|---|---|---|---|
| PUT | `/users/me` | Member/Admin | `{fullName, email?}` → 200 (blank email removes it; 409 `EMAIL_IN_USE`). Role/status/mobile fields are not accepted (no mass assignment). |
| PUT | `/users/me/password` | Member/Admin | `{currentPassword, newPassword}` → 204 |
| POST | `/users/me/mobile` | Member/Admin | `{newMobile, currentPassword}` → 202, SMS code to the **new** number (409 `MOBILE_IN_USE`, 400 `SAME_MOBILE`) |
| POST | `/users/me/mobile/confirm` | Member/Admin | `{code}` → 200 `Me` with the new number |

## Public catalogue

| Method | Path | Access | Notes |
|---|---|---|---|
| GET | `/categories` | Public | Active categories |
| GET | `/books` | Public | `?q=&categoryId=&page=&size=&sort=` → `PageResponse<BookSummary{id,title,description,category,coverUrl}>` |
| GET | `/books/{id}` | Public | `BookDetail` (no storage keys, no PDF URLs); 404 if not available |
| GET | `/books/{id}/cover` | Public | Image stream, `Cache-Control: public, max-age=86400` |

## Member PDF access

| Method | Path | Access | Notes |
|---|---|---|---|
| GET | `/books/{id}/read-access` | Member | → `{ticket, expiresAt, versionId, pageCount}`; 403 `NOT_ELIGIBLE` otherwise |
| GET | `/books/{id}/download-access` | Member | Same, ticket scoped to download |
| GET | `/books/{id}/content?ticket=` | Ticket | `application/pdf`, Range supported, `Content-Disposition: inline` (read) / `attachment; filename="<safe>.pdf"` (download), `Cache-Control: private, no-store` |
| GET | `/members/books/{id}/progress` | Member | `{lastPage, versionId, updatedAt}` or 404 |
| PUT | `/members/books/{id}/progress` | Member | `{lastPage}` → 200 |
| GET | `/members/progress` | Member | Reading history page |
| POST | `/modification-requests` | Member | `{bookId?, requestType, description}` → 201 |
| GET | `/members/modification-requests` | Member | Own requests only |

Ticket: HMAC-signed, contains user id, book id, version id, purpose, 60-s expiry. Re-checked eligibility is cheap, so `/content` re-validates account + membership + book on each request.

## Admin — all require `ADMIN`

| Method | Path | Notes |
|---|---|---|
| GET | `/admin/stats` | Dashboard counts: `{users{total,active,disabled,locked,unverified,activeAdmins}, membershipsValidToday, booksByStatus{DRAFT,PUBLISHED,INACTIVE,ARCHIVED}, pendingRequests, asOf}`. "Valid today" uses the business timezone, same rule as `MembershipValidity` |
| GET/POST | `/admin/users` | List (`?q=` matches name, mobile or email; `&status=&role=`), create `{fullName, mobile, email?, password, roles[], mobileVerified}` (the admin may vouch for the number) |
| GET/PUT | `/admin/users/{id}` | `{fullName, mobile, mobileVerified, email?, accountStatus, roles[], version}` (409 `MOBILE_IN_USE` / `EMAIL_IN_USE`); last active admin cannot be demoted/disabled (409 `LAST_ADMIN`) |
| GET/POST | `/admin/memberships` | `?userId=&status=`; create new record (previous ACTIVE must be ended first or is auto-ended) |
| PUT | `/admin/memberships/{id}` | `{startDate, endDate, status, notes, version}` |
| GET/POST | `/admin/categories` | |
| PUT | `/admin/categories/{id}` | `{name, description, active, version}` |
| GET | `/admin/books` | All statuses, `?status=&q=` |
| POST | `/admin/books` | `multipart/form-data`: `metadata` (JSON part), `cover` (optional), `pdf` (required) → 201 |
| GET/PUT | `/admin/books/{id}` | Metadata only `{title, description, categoryId, effectiveDate, expiryDate, status, version}` |
| PUT | `/admin/books/{id}/cover` | multipart `cover` |
| GET | `/admin/books/{id}/versions` | Version history |
| GET | `/admin/books/{id}/versions/{versionId}/content` | Download any version (incl. historical) |
| POST | `/admin/books/{id}/versions` | multipart `pdf`, `changeNote` → new current version; old kept |
| POST | `/admin/books/{id}/archive` | → 204 |
| GET | `/admin/modification-requests` | `?status=` |
| PUT | `/admin/modification-requests/{id}` | `{status: APPROVED|REJECTED, reviewNote, version}` |
| GET | `/admin/audit-logs` | `?actorId=&action=&entityType=&entityId=&from=&to=` |

## Operations

| GET | `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` | Public, no details |
|---|---|---|
