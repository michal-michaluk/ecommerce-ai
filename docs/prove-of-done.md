# Prove of Done — offer management, implementation increment (spec §11, I1–I7)

Executed against the live k3s stack (`offer-management/.k3s-kubeconfig`, app `http://localhost:8080`,
Keycloak `http://localhost:18081`) on branch `feat/offer-management`.
Every row below is **observed output**, not expectation. Nothing was weakened to make a row pass.

## Summary

| # | Facet | Executed | Result |
|---|---|---|---|
| I1 | Artifact | `./gradlew clean build` | **PASS** — BUILD SUCCESSFUL (40s) with `:spotbugsTest`, `:spotbugsMain` and `:jacocoTestCoverageVerification` all green; container starts. Initially FAIL: `:spotbugsTest` aborted the build on `DM_DEFAULT_ENCODING` in `DraftControllerTest` (`String.getBytes()` without a charset) — fixed with `StandardCharsets.UTF_8`. |
| I2 | Contract | `bash offer-management/e2e/run-e2e.sh` | **PARTIAL** — 41 pairs, 39 pass / 2 fail |
| I3 | Failure | per-pair evidence from the hurl run | **PASS** — all 16 declared error pairs return their declared code + status |
| I4 | No false green | live-API publish with no price | **PASS** — `422 PUBLICATION_BLOCKED` with `details.blocking` |
| I5 | Gate | `./gradlew test` | **PASS** — BUILD SUCCESSFUL, 317 tests / 0 failures; 5 context ArchUnit classes green |
| I6 | Integration | `bash offer-management/e2e/run-integration.sh` | **PASS** — 5 passed / 0 failed, read model reflects the published version |
| I7 | Value | metric "share of drafts reaching `PUBLISHED` without a rejection round" | **UNPROVABLE / NOT MEASURED** — no baseline was ever captured |

---

## I1 — Artifact

- **Command:** `cd offer-management && ./gradlew clean build --no-daemon` (redirected to a file; Gradle output
  is never piped into `grep`/`tail`/`head`).
- **Observed:** `> Task :test` passed, `:jacocoTestReport`, `:jacocoTestCoverageVerification`, `:spotbugsMain`
  passed, then:

  ```
  > Task :spotbugsTest FAILED
  * What went wrong:
  Execution failed for task ':spotbugsTest' (registered by plugin 'com.github.spotbugs').
  > Verification failed: SpotBugs ended with exit code 1. See the report at .../build/reports/spotbugs/test.html
  BUILD FAILED in 38s
  ```

- **Root cause (verified, reproducible):** `./gradlew spotbugsTest --no-daemon` → `exit=1`, `BUILD FAILED`.
  The single violation in `build/reports/spotbugs/test.html`:

  ```
  Found reliance on default encoding in com.example.offer.draft.DraftControllerTest.unsupportedPhotoFormatIsUnprocessable(): String.getBytes()
  ```

  source: `offer-management/src/test/java/com/example/offer/draft/DraftControllerTest.java:154` —
  `upload(productId, "%PDF-1.7".getBytes(), "scan.pdf", "application/pdf")`.
- **RESOLVED:** `String.getBytes()` now passes `StandardCharsets.UTF_8`
  (`DraftControllerTest.java:154`), and `./gradlew clean build` succeeds with `:spotbugsTest`,
  `:spotbugsMain` and `:jacocoTestCoverageVerification` all green (verified on HEAD, commit `3e9ce7c`).
- **Container starts (same row's second facet):** k3s pod `offer-management-… 1/1 Running`;
  `curl http://localhost:8080/actuator/health` → `{"groups":["liveness","readiness"],"status":"UP"}`.
- **Result: PASS** (after the fix above; the initial run was FAIL).

## I2 — Contract (41 pairs, 25 `2xx` / 16 `4xx`)

- **Command:** `bash offer-management/e2e/run-e2e.sh` (mints carla/marta/sara tokens, seeds the photo fixture,
  runs `hurl --test --continue-on-error` over `offer-management/e2e/offer-management.hurl`).
- **Observed:** `Executed requests: 41`, `Failed files: 1 (100.0%)`, 2 assertion errors reported:

  ```
  error: Assert status code --> offer-management.hurl:250:6
     POST .../review-requests/{{review_request_id}}/rejection   HTTP 200   actual value is <409>
  error: Assert status code --> offer-management.hurl:324:6
     POST .../products/{{product_id}}/publications              HTTP 201   actual value is <409>
  ```

  → **41 pairs, 39 pass / 2 fail.** The authoritative `.hurl` declares 25 `2xx` and 16 `4xx` pairs.
- **The 2 failing pairs are declared-`2xx` pairs 22 and 27** (the spec contradicting its own wording, not the
  error contract):
  - **pair 22** (`offer-management.hurl:250`, declared `HTTP 200`) — reject a review that was already approved.
    D5 ("a review is decided once") means it is no longer pending, so the domain returns `409 REVIEW_NOT_PENDING`.
  - **pair 27** (`offer-management.hurl:324`, declared `HTTP 201`) — re-publish the same description version.
    RULE-10 freezes a version once, so the domain returns `409 VERSION_NOT_APPROVED`.
- **Result: PARTIAL** — 39/41 request pairs pass; both failures are `2xx` pairs contradicted by the domain rules,
  **not** any declared error pair.

## I3 — Failure (the 16 declared error pairs)

Reproduced with the same suite and captured per request via `hurl --report-json` (declared = the `.hurl`
`HTTP <status>` + `jsonpath "$.code"`; observed = the actual response status + body `code`). Every one
matched its declared status **and** code, and all `$.code` assertions passed.

| Pair | line | Method + path | Declared | Observed status | Observed code | Match |
|---|---|---|---|---|---|---|
| 1 | 21 | GET `/products` (no token) | 401 `UNAUTHENTICATED` | 401 | `UNAUTHENTICATED` | pass |
| 2 | 27 | GET `/products` (sales) | 403 `FORBIDDEN` | 403 | `FORBIDDEN` | pass |
| 3 | 38 | POST `/products` (bad body) | 422 `VALIDATION_FAILED` | 422 | `VALIDATION_FAILED` | pass |
| 9 | 109 | GET `/products/p-does-not-exist/description-draft` | 404 `PRODUCT_NOT_FOUND` | 404 | `PRODUCT_NOT_FOUND` | pass |
| 12 | 144 | POST `…/description-draft/photos` (too small) | 422 `PHOTO_TOO_SMALL` | 422 | `PHOTO_TOO_SMALL` | pass |
| 13 | 153 | POST `…/description-draft/photos` (bad format) | 422 `PHOTO_FORMAT_UNSUPPORTED` | 422 | `PHOTO_FORMAT_UNSUPPORTED` | pass |
| 16 | 186 | POST `…/review-requests` (already pending) | 409 `REVIEW_ALREADY_PENDING` | 409 | `REVIEW_ALREADY_PENDING` | pass |
| 17 | 193 | PUT `…/description-draft` (under review) | 409 `DRAFT_NOT_EDITABLE` | 409 | `DRAFT_NOT_EDITABLE` | pass |
| 20 | 228 | POST `/review-requests/…/approval` (author) | 403 `REVIEWER_IS_AUTHOR` | 403 | `REVIEWER_IS_AUTHOR` | pass |
| 23 | 256 | GET `/review-requests/r-unknown` | 404 `REVIEW_NOT_FOUND` | 404 | `REVIEW_NOT_FOUND` | pass |
| 24 | 270 | POST `…/publications` (no price) | 422 `PUBLICATION_BLOCKED` | 422 | `PUBLICATION_BLOCKED` | pass |
| 31 | 369 | POST `…/prices` (bad date range) | 422 `INVALID_DATE_RANGE` | 422 | `INVALID_DATE_RANGE` | pass |
| 32 | 383 | POST `…/prices` (content-manager) | 403 `FORBIDDEN` | 403 | `FORBIDDEN` | pass |
| 37 | 435 | POST `…/publications` (unapproved version) | 409 `VERSION_NOT_APPROVED` | 409 | `VERSION_NOT_APPROVED` | pass |
| 38 | 444 | POST `…/versions/v99/revert` | 404 `VERSION_NOT_FOUND` | 404 | `VERSION_NOT_FOUND` | pass |
| 41 | 461 | DELETE `/products/p-does-not-exist/offer-presence` | 404 `PRODUCT_NOT_FOUND` | 404 | `PRODUCT_NOT_FOUND` | pass |

- **Result: PASS — 16/16** declared error pairs return their declared code and status.
- **Known gap (uncovered, not a failure of this row):** `PRICE_OVERLAP` (declared `409` in element 02) has
  **no pair** in the authoritative 41-pair suite, so it is not exercised here.

## I4 — No false green (publish with no price)

- **Command (live API, real tokens):**
  - mint carla (`content_manager`) / marta (`reviewer`) tokens;
  - `POST /products` → product id;
  - `PUT /products/{id}/description-draft` + `POST …/photos` (valid JPEG);
  - `POST …/review-requests` then `POST /review-requests/{rr}/approval` as marta;
  - **no price scheduled**;
  - `curl -X POST /products/{id}/publications -d '{"descriptionVersion":"v1","availableFrom":null}'`.
- **Observed:** `HTTP_STATUS=422`

  ```json
  {"code":"PUBLICATION_BLOCKED",
   "message":"The description cannot be published while the quality gate reports items.",
   "details":{"blocking":[{"code":"PRICE_REQUIRED","label":"Price valid for a date range"}]}}
  ```

- **Result: PASS** — `422 PUBLICATION_BLOCKED` with a populated `details.blocking` array.

## I5 — Gate (ArchUnit per context)

- **Command:** `cd offer-management && ./gradlew test --no-daemon --rerun-tasks` (full suite, tests actually
  executed, not `--tests`-filtered).
- **Observed:** `> Task :test`, `BUILD SUCCESSFUL in 33s`. Parsed from `build/test-results/test/*.xml`:
  `files=44 tests=317 failures=0 errors=0 skipped=0`.

  | ArchUnit class | tests | failures |
  |---|---|---|
  | `com.example.offer.catalog.ArchitectureOfCatalogContextTest` | 6 | 0 |
  | `com.example.offer.draft.ArchitectureOfDraftContextTest` | 6 | 0 |
  | `com.example.offer.offer.ArchitectureOfOfferContextTest` | 6 | 0 |
  | `com.example.offer.pricing.ArchitectureOfPricingContextTest` | 6 | 0 |
  | `com.example.offer.publishing.ArchitectureOfPublishingContextTest` | 6 | 0 |

- **Result: PASS** — 5/5 context `ArchitectureOf{Context}Test` classes green (30 rules total), full suite
  317 tests / 0 failures. No rule weakened.

## I6 — Integration (event reaches the `browsing offer` consumer)

- **Command:** `bash offer-management/e2e/run-integration.sh` (drives the real lifecycle over HTTP with role
  tokens, then reads the in-cluster consumer's materialised read model after each boundary event).
- **Observed:**
  - `PASS ProductVersionPublishedToOffer reached the consumer; present=true, version=v1`
  - `PASS ProductPricesChanged reached the consumer; price={… "price": {"value":"259.00","currency":"PLN"}, "discountPercent":"10" …}`
  - `PASS ProductRemovedFromOffer reached the consumer; present=false`
  - `topic event types: ProductPricesChanged_v1, ProductRemovedFromOffer_v1, ProductVersionPublishedToOffer_v1`
  - `PASS no private event crossed the boundary (topic types are a subset of the three)`
  - `PASS all three boundary event types were observed on topic browsing-offer`
  - summary: **`5 passed, 0 failed`**

  Example read model after publish:
  `{"productId":"p-ad90c372-…","title":"Kosiarka ręczna 340 (I6)","version":"v1","availableFrom":null,"price":null,"discountPercent":null,"present":true,"lastEvent":"ProductVersionPublishedToOffer_v1","events":1}`
- **Result: PASS** — an event reaches the consumer and its read model updates (the spec's I6 expectation).

## I7 — Value (metric baseline → target)

- **Spec metric (§11 Definition of Value):** *share of drafts reaching `PUBLISHED` without a rejection round,
  baseline → target within 60 days.*
- **Observed:** no baseline measurement exists anywhere in the repository or the spec; the spec itself records
  this as an open topic (`### Open Topics`: "I7's metric has no baseline yet — must be captured before the
  increment closes"). No instrumented counter, dashboard, or historical dataset for this metric was found.
- **Result: UNPROVABLE / NOT MEASURED.** Without a recorded baseline there is nothing to move off, and no
  target timeframe can be evaluated. No number is invented here and the row is **not** marked pass. To close:
  instrument "drafts reaching `PUBLISHED` without a prior rejection event", capture a baseline over a real
  window, then re-run after the target window.

---

## Environment notes

- Stack: k3s in a privileged **podman** container; `KUBECONFIG=offer-management/.k3s-kubeconfig`.
- Keycloak port-forward `http://localhost:18081` was live throughout; app ingress `http://localhost:8080`.
- The I6 run restarted `deploy/offer-management` (startup price sweep) mid-suite; pods returned to
  `1/1 Running` and health remained `UP` after.
