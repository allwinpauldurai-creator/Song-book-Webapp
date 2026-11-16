# Database ERD

Authoritative schema: `backend/src/main/resources/db/migration/V1__initial_schema.sql`.

```mermaid
erDiagram
    users ||--o{ user_roles : has
    roles ||--o{ user_roles : grants
    users ||--o{ memberships : holds
    users ||--o{ user_tokens : "verification/reset"
    users ||--o{ refresh_tokens : sessions
    book_categories ||--o{ books : classifies
    file_assets ||--o| books : cover
    books ||--o{ book_versions : versions
    book_versions ||--o| books : "current_version"
    file_assets ||--|| book_versions : pdf
    users ||--o{ reading_progress : reads
    books ||--o{ reading_progress : tracked
    users ||--o{ modification_requests : submits
    books ||--o{ modification_requests : about
    users ||--o{ audit_logs : actor

    users {
      uuid id PK
      varchar email UK "lower-case, trimmed"
      varchar password_hash
      varchar account_status "ACTIVE|DISABLED|LOCKED"
      bool email_verified
      timestamptz email_verified_at
      int failed_login_count
      timestamptz locked_until
      bigint version
    }
    memberships {
      uuid id PK
      uuid user_id FK
      date start_date
      date end_date "NULL = no end"
      varchar status "ACTIVE|SUSPENDED|EXPIRED|CANCELLED"
    }
    books {
      uuid id PK
      varchar title
      uuid category_id FK
      uuid cover_asset_id FK
      date effective_date
      date expiry_date "NULL = no expiry"
      varchar status "DRAFT|PUBLISHED|INACTIVE|ARCHIVED"
      uuid current_version_id FK
    }
    book_versions {
      uuid id PK
      uuid book_id FK
      int version_number "UK with book_id"
      uuid pdf_asset_id FK
    }
    file_assets {
      uuid id PK
      varchar container_name
      varchar blob_name
      varchar content_type
      bigint file_size
      char checksum_sha256
      int page_count
    }
```

## Constraints and rules

| Table | Constraint | Purpose |
|-------|-----------|---------|
| users | `email = lower(btrim(email))`, unique | Normalized unique email |
| users | `email_verified = false OR email_verified_at IS NOT NULL` | Consistent verification state |
| roles | name in (MEMBER, ADMIN) | No `GUEST` role in DB |
| memberships | `end_date >= start_date`; partial unique `(user_id) WHERE status='ACTIVE'` | Valid range; one active membership |
| user_tokens / refresh_tokens | unique `token_hash` (SHA-256 hex) | Raw tokens never stored |
| book_categories | unique `lower(name)` | Case-insensitive uniqueness |
| file_assets | `file_size > 0`; content_type allow-list; unique (container, blob) | No empty/unknown files |
| books | `expiry_date >= effective_date`; PUBLISHED ⇒ `current_version_id` not null | Valid window; no published book without PDF |
| books → book_versions | composite FK `(current_version_id, id)` → `(id, book_id)`, deferrable | Current version must belong to the same book |
| book_versions | unique (book_id, version_number); `version_number > 0` | Ordered history |
| reading_progress | unique (user_id, book_id); `last_page >= 1` | One row per user/book |
| modification_requests | reviewed_at set iff status ≠ PENDING | Consistent review state |

## Deletion / archival

- Users: never hard-deleted in v1 (disable instead). FKs from history tables use `RESTRICT`/`SET NULL`.
- Books: archived, never deleted. `book_versions` and `file_assets` use `RESTRICT`.
- Categories: deactivated; `RESTRICT` while referenced.
- Tokens: cascade-deleted with user; expired rows purged by a scheduled job (Phase 3).

## Indexes

Email (unique), `user_roles(role_id)`, `memberships(user_id,status,start_date,end_date)`,
`books(status,effective_date,expiry_date)`, `books(category_id)`, `books(lower(title))`,
`reading_progress(user_id,updated_at desc)`, `audit_logs(created_at)`, `(actor,created_at)`,
`(entity_type,entity_id)`, `(action,created_at)`, `modification_requests(status,created_at)`.
Full-text search (`tsvector` on title+description) can be added in Phase 7 if `ILIKE` is too slow.
