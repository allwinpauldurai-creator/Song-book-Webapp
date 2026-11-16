# Requirements Review & Software Requirements Specification

Source: `SONG_BOOK_LIBRARY_CLAUDE_CODE_GUIDE.md`. This document records how the brief was
interpreted, the gaps found and the decisions taken (or still needed).

## 1. Scope summary

Single-catalogue public web app. Guests browse metadata; self-registered **members** with a verified
email, an active account and a valid membership can read (PDF.js) and download PDFs; **admins**
manage users, memberships, categories, books, file versions, modification requests and audit logs.
Modular-monolith Spring Boot API + React SPA on Azure.

## 2. Functional requirements (traceable IDs)

| ID | Requirement | Phase |
|----|-------------|-------|
| FR-01 | Guest browses published, in-window books (cover, title, description, category) with search, category filter, sort, pagination | 7 |
| FR-02 | Guest views book detail page (metadata only, no storage keys/URLs) | 7 |
| FR-03 | Self-registration (name, email, password) → unverified `MEMBER`, never `ADMIN` | 3 |
| FR-04 | Email verification via single-use expiring token (hash stored) | 3 |
| FR-05 | Login / refresh / logout with token rotation and revocation | 3 |
| FR-06 | Forgot / reset password via single-use expiring token; generic responses | 3 |
| FR-07 | Rate limiting on register, login, verify, forgot/reset | 3 |
| FR-08 | Member views/updates own profile (name; email change out of scope v1) | 4 |
| FR-09 | Admin CRUD users, enable/disable, grant/revoke `ADMIN`, last-admin safeguard | 4 |
| FR-10 | Admin manages membership records (history preserved) | 4 |
| FR-11 | Admin manages categories | 5 |
| FR-12 | Admin creates/edits books with cover, PDF, effective date, optional expiry, status | 5–6 |
| FR-13 | Admin uploads new PDF version; previous versions retained | 5–6 |
| FR-14 | Admin archives a book (soft; never hard-deleted) | 5 |
| FR-15 | Eligible member reads PDF online | 8 |
| FR-16 | Eligible member downloads PDF | 8 |
| FR-17 | Reading progress (last page) saved and listed as history | 9 |
| FR-18 | Member submits correction/addition requests; admin reviews | 9 |
| FR-19 | Audit log of admin and security-relevant actions, searchable by admin | 9 |

## 3. Non-functional requirements

| ID | Requirement |
|----|-------------|
| NFR-01 | Backend-enforced, deny-by-default authorization; 401 vs 403 semantics |
| NFR-02 | No secrets in source; Managed Identity + Key Vault in Azure |
| NFR-03 | PDFs never publicly reachable; no SAS/URLs/tokens in logs |
| NFR-04 | Flyway-owned schema, `ddl-auto=validate` |
| NFR-05 | Calendar rules evaluated with one injected `Clock` in a configured business timezone |
| NFR-06 | Responsive, WCAG 2.1 AA-oriented UI |
| NFR-07 | p95 catalogue API < 500 ms at expected load (to be confirmed) |
| NFR-08 | Daily DB backups, 7–35 day retention (to be confirmed) |

## 4. Contradictions / ambiguities found and resolution

| # | Issue | Resolution |
|---|-------|------------|
| A1 | §16 says *Phase 1 only, no code*; the user asked to build the Spring Boot app | Phase 1 documents **and** the Phase 2 backend foundation were delivered together. Stopping here for approval. |
| A2 | Default membership duration not defined; "don't assume perpetual" | Configurable `songbook.membership.default-duration-days`; **placeholder 365**. 0 = no end date. **Needs business confirmation (D1).** |
| A3 | Membership is created when? Registration alone vs. admin approval | **Assumed:** an `ACTIVE` membership is created automatically on email verification. Alternative: admin must activate. **Needs confirmation (D2).** |
| A4 | SAS URL vs. backend streaming for PDFs | **Chosen: authenticated backend streaming** (see SECURITY.md §5). |
| A5 | `GET /books/{id}/read-access` returns what? | Returns a short-lived (60 s), single-book, single-user **access ticket**; PDF.js then fetches `GET /books/{id}/content?ticket=…`, supporting HTTP Range. See API spec. |
| A6 | Category deletion not specified | Categories are deactivated, never deleted while referenced (FK `RESTRICT`). |
| A7 | Book deletion not specified | Books are archived, never hard-deleted (versions/history kept). |
| A8 | Cover image "required or configurable" | Optional in v1; placeholder cover rendered by UI. |
| A9 | Profile update fields not specified | Members may change full name and password only. Email change deferred. |
| A10 | Account lockout policy unspecified | 5 failed logins → 15-min lock (`locked_until`), plus IP rate limit. |
| A11 | Public category list endpoint missing in §9 | Added `GET /api/v1/categories` (public) and `/admin/categories` CRUD. |
| A12 | Admin book listing/version history endpoints missing | Added `GET /admin/books`, `GET /admin/books/{id}`, `GET /admin/books/{id}/versions`. |
| A13 | Can an admin read PDFs without a membership? | **Assumed yes** (admins bypass the membership check, still need active account). **Confirm (D3).** |
| A14 | Can members read books that are in-window but INACTIVE? | No — same eligibility as public catalogue. |

## 5. Decisions needed from the business owner

- **D1** Default membership duration (days, or no expiry).
- **D2** Is membership automatic after email verification, or admin-approved?
- **D3** Can admins read/download PDFs without a membership record?
- **D4** Email provider: Azure Communication Services Email (recommended) or another.
- **D5** Business timezone for date rules (e.g. `Asia/Kolkata`, `UTC`).
- **D6** Max PDF size (default 100 MB) and whether downloads should be watermarked (out of scope v1).
- **D7** Data retention for audit logs and deleted/disabled accounts (privacy/GDPR).
- **D8** Production domain names (needed for CORS, cookies, email links).

## 6. Key risks

| Risk | Mitigation |
|------|-----------|
| Content leakage (members share PDFs) | Accept (no DRM, per brief); audit downloads; optional watermarking later |
| Blob/DB inconsistency | Staging upload + cleanup queue + reconciliation job |
| Account enumeration | Generic responses on register/forgot; uniform timing where practical |
| Credential stuffing | Rate limiting + lockout + BCrypt |
| Large-file memory pressure | Stream uploads/downloads; never load whole PDF into heap |
| Clock/timezone bugs at date boundaries | Single `Clock`, `LocalDate`, inclusive-range tests |
