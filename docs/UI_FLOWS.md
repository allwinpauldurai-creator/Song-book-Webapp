# Frontend Page Flows & Wireframes

React + TypeScript + Material UI (Vite). Route guards are UX only; the API is the security boundary.

## Routes

| Route | Access | Page |
|---|---|---|
| `/` | Public | Landing: hero, search box, featured/latest books |
| `/books` | Public | Catalogue grid: search, category chips, sort, pagination |
| `/books/:id` | Public | Detail: cover, title, category, description; CTA "Log in to read" / "Read" + "Download" |
| `/login`, `/register`, `/verify-email?token=`, `/forgot-password`, `/reset-password?token=` | Public | Auth forms |
| `/me` | Member | Dashboard: membership status card, continue reading, recent history |
| `/me/profile` | Member | Name, change password |
| `/me/requests` | Member | My modification requests + new request form |
| `/read/:id` | Member | PDF.js reader |
| `/admin` | Admin | Counts: users, active memberships, books by status, pending requests |
| `/admin/users`, `/admin/users/:id` | Admin | Table + edit drawer (status, roles, memberships tab) |
| `/admin/memberships` | Admin | Filterable table, create/edit dialog |
| `/admin/books`, `/admin/books/new`, `/admin/books/:id` | Admin | Table; form with cover/PDF upload; versions tab |
| `/admin/categories` | Admin | Inline-editable list |
| `/admin/requests` | Admin | Review queue |
| `/admin/audit` | Admin | Filterable log viewer |

## Key flows

```mermaid
flowchart LR
  A[Guest: /books] --> B[/books/:id]
  B -->|Read| C{Logged in?}
  C -- no --> D[/login?next=/read/:id]
  D --> E{Eligible?}
  C -- yes --> E
  E -- yes --> F[/read/:id PDF.js]
  E -- unverified --> G[Banner: verify email + resend]
  E -- membership invalid --> H[Banner: membership expired/suspended - contact admin]
```

Registration: `/register` → "Check your email" → link `/verify-email?token=…` → success → `/login`.

## Wireframes

Catalogue (desktop; collapses to 1–2 columns on mobile):
```
+--------------------------------------------------------------+
| LOGO   Catalogue              [Search.............]  Login   |
+--------------------------------------------------------------+
| [All] [Hymns] [Choruses] [Youth] ...      Sort: [Title  v]   |
| +--------+  +--------+  +--------+  +--------+               |
| | cover  |  | cover  |  | cover  |  | cover  |               |
| |        |  |        |  |        |  |        |               |
| +--------+  +--------+  +--------+  +--------+               |
| Title       Title       Title       Title                    |
| Category    Category    Category    Category                 |
| 2-line desc 2-line desc 2-line desc 2-line desc              |
|                 < 1 2 3 ... 9 >                              |
+--------------------------------------------------------------+
```

Reader:
```
+--------------------------------------------------------------+
| <- Back  Title             [<] [ 12 ]/240 [>]  [-] 100% [+]   |
|                            [Fit width] [Search] [Fullscreen] |
+--------------------------------------------------------------+
|                                                              |
|                    rendered PDF page                         |
|                                                              |
+--------------------------------------------------------------+
| (mobile: bottom bar with prev/next + page, swipe to turn)    |
+--------------------------------------------------------------+
```

Admin book form:
```
Title*            [..........................]
Category          [Select v]
Description       [..........................]
Cover image       [Choose file]  (JPEG/PNG/WebP, max 5 MB)
PDF*              [Choose file]  (max 100 MB)
Effective date*   [2026-11-16]
Expiry date       [          ]  Leave blank for "No expiry"
Status            (o) Draft ( ) Published
                                   [Cancel] [Save]
```

All lists have loading skeletons, empty states ("No books match your search") and error alerts with retry.
