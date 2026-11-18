# Testing

## Commands

```bash
cd backend
./mvnw verify          # compile + all tests (Windows: mvnw.cmd verify)
./mvnw test -Dtest=LibraryFlowTests
```

Integration tests start the **full application against a real PostgreSQL 16** using embedded
binaries (`io.zonky.test:embedded-postgres`) — **no Docker required**, works on Windows, macOS and
Linux CI. Flyway runs the real migrations and Hibernate validates the schema, so mapping errors fail
the build. SMS codes are captured in memory (`TestInfrastructure.CapturingSmsSender`); files go to
`backend/target/test-storage`.

## Test layers

| Layer | Tooling | Classes |
|---|---|---|
| Domain rules | JUnit 5, AssertJ | `BookAvailabilityTest`, `MembershipValidityTest` |
| Security components | JUnit 5 | `AccessTicketServiceTest`, `RateLimiterTest` |
| End-to-end API (real DB) | `@SpringBootTest` + MockMvc + embedded PostgreSQL | `AuthFlowTests`, `LibraryFlowTests` |
| Frontend unit | Vitest (`cd frontend && npm test`) | `client.test.ts` (token refresh), `redirect.test.ts`, `ticketedRangeLoader.test.ts` (ticket renewal, retries, version change), `usePdfSearch.test.ts` |
| Frontend components (later) | React Testing Library, MSW | |
| Browser E2E + accessibility | Playwright 1.65 + axe-core (`cd frontend && npm run test:e2e`; backend running and seeded once with `node scripts/seed-sample-data.mjs`) | `e2e/public.spec.ts` (guest flows, WCAG 2.1 AA light + dark, PDF.js assets), `e2e/signed-in.spec.ts` (reader, progress, download, member and admin pages + axe). One login per run (backend rate limit) |
| Browser E2E, production build | `npm run test:e2e:prod` (same origin) and `npm run test:e2e:cross-origin` (production topology: API on another origin, no proxy, real CSP); both fail on any CSP violation | Same specs, `playwright.prod.config.ts` / `playwright.cross-origin.config.ts` |
| Post-deployment smoke | `node scripts/smoke-test.mjs <frontend> <api>` (run by `deploy.yml`) | readiness, catalogue, headers, API docs off, CORS, CSP, SPA fallback, PDF.js wasm |
| Performance | `node scripts/perf-load-test.mjs http://localhost:8080 20` (results in `docs/SECURITY_REVIEW.md` §4) | NFR-07 catalogue p95 < 500 ms |

## Acceptance coverage map (requirements §14)

| Requirement | Covered by |
|---|---|
| 14.1 registration success, duplicate mobile, mobile normalisation, invalid input, hash-only password and code, MEMBER-only role | `AuthFlowTests`, `PhoneNumbersTest` |
| 14.1 unverified user blocked; SMS codes expire, are single-use, purpose-bound and burn after 5 wrong guesses; lockout | `AuthFlowTests` |
| SMS resend cooldown and daily limit; mobile change; MSG91 request format; `log` provider refused unless allowed | `OtpServiceTest`, `AuthFlowTests`, `SmsProviderTest` |
| 14.1 rate limiting | `RateLimiterTest` (disabled in integration tests to keep them independent) |
| 14.2 guest browse / no PDF; member 403 on admin APIs; admin operations; disabled user 401; IDOR | `AuthFlowTests`, `LibraryFlowTests` |
| 14.3 active / suspended / expired / future-start membership, null end date | `MembershipValidityTest`, `LibraryFlowTests.suspendedOrExpiredMembershipDeniesAccess` |
| 14.4 future / expired / draft / inactive / archived books; null expiry | `BookAvailabilityTest`, `LibraryFlowTests` |
| 14.5 valid PDF, disguised PDF, malformed PDF, invalid image, oversized file | `LibraryFlowTests.invalidUploadsAreRejected` |
| 14.5 version replacement keeps old version; old ticket rejected | `LibraryFlowTests.replacingPdfKeepsPreviousVersion` |
| 14.5 unauthorized content access denied; tickets expire / tamper-proof | `LibraryFlowTests`, `AccessTicketServiceTest` |
| 14.5 storage failure / DB failure after upload | **Not yet automated** — compensation code in `FileAssetService`; needs a fault-injection test |
| HTTP Range (PDF.js streaming) | `LibraryFlowTests.eligibleMemberReadsAndDownloadsWithRangeSupport` |
