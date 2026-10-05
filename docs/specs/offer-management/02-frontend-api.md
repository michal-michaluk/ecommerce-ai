# Element 02 — Frontend API

The HTTP contract the offer-management UI calls. Derived from the element-01 mockups:
every screen maps to one or more endpoints, and every blocked/error state a mockup shows
has a declared status + error body.

**Artifact:** [`prototypes/frontend-api.hurl`](prototypes/frontend-api.hurl) — executable
and assertable. `hurlfmt --check` passes; 41 request/response pairs — 25 success (2xx),
16 error (4xx/5xx) — carrying 84 `jsonpath` assertions.

**Full response shapes:** [`prototypes/frontend-api.responses.md`](prototypes/frontend-api.responses.md)
— every field of every response, success and error.

**Why shapes are a separate artifact** — verified on hurl 7.1.0: an inline JSON body in
`.hurl` is matched **exactly**, not as a subset. A response that carries an extra key fails
the assertion (`error: Assert body value`, actual shown with the extra field). Inlining
full bodies on all 41 pairs would therefore break on every added field and would need
`[Captures]` for each generated `productId`/timestamp. The `.hurl` file keeps executable
behaviour; the shapes file is the readable contract.

## Auth

OAuth2 / OIDC JWT bearer (blueprint `docs/arch/microservice-java-spring/security.md`) —
deny by default, every endpoint below requires a token.

| Role | Reaches |
|---|---|
| `content-manager` | products, drafts, photos, reviews, publication, versions, removal |
| `sales` | prices and discounts |

## Endpoint set

| Screen | Method + path | Success | Errors |
|---|---|---|---|
| 01 Catalog | `GET /products?page&size&state&query` | 200 | 401, 403 |
| 01 Catalog | `GET /products/{productId}` | 200 | 401, 403, 404 |
| 02 Create | `POST /products` | 201 | 401, 403, 422 |
| 03 Draft | `GET /products/{productId}/description-draft` | 200 | 401, 403, 404 |
| 03 Draft | `PUT /products/{productId}/description-draft` | 200 | 401, 403, 404, 409, 422 |
| 03 Draft | `POST /products/{productId}/description-draft/review-requests` | 201 | 401, 403, 404, 409 |
| 04 Photos | `GET /product-photo-formats` | 200 | 401 |
| 04 Photos | `POST /products/{productId}/description-draft/photos` | 201 | 401, 403, 404, 422 |
| 04 Photos | `DELETE /products/{productId}/description-draft/photos/{photoId}` | 204 | 401, 403, 404 |
| 05 Review queue | `GET /review-requests?status` | 200 | 401, 403 |
| 06 Review detail | `GET /review-requests/{reviewRequestId}` | 200 | 401, 403, 404 |
| 06 Review detail | `POST /review-requests/{reviewRequestId}/approval` | 200 | 401, 403, 404, 409 |
| 06 Review detail | `POST /review-requests/{reviewRequestId}/rejection` | 200 | 401, 403, 404, 409 |
| 07 Publish | `GET /products/{productId}/publication` | 200 | 401, 403, 404 |
| 07 Publish | `POST /products/{productId}/publications` | 201 | 401, 403, 404, 409, 422 |
| 08 Pricing | `GET /products/{productId}/prices` | 200 | 401, 403, 404 |
| 08 Pricing | `POST /products/{productId}/prices` | 201 | 401, 403, 404, 422 |
| 08 Pricing | `PUT /products/{productId}/prices/{priceId}` | 200 | 401, 403, 404, 409, 422 |
| 08 Pricing | `DELETE /products/{productId}/prices/{priceId}` | 204 | 401, 403, 404 |
| 09 Versions | `GET /products/{productId}/versions` | 200 | 401, 403, 404 |
| 09 Versions | `POST /products/{productId}/versions/{version}/revert` | 201 | 401, 403, 404 |
| 09 Removal | `DELETE /products/{productId}/offer-presence` | 204 | 401, 403, 404 |

## Error codes

| Code | Status | Raised by |
|---|---|---|
| `VALIDATION_FAILED` | 422 | create product, save draft (`fields[]` names the offending field) |
| `PRODUCT_NOT_FOUND` | 404 | every `/products/{productId}/**` |
| `REVIEW_NOT_FOUND` | 404 | `/review-requests/{id}` |
| `VERSION_NOT_FOUND` | 404 | revert |
| `UNAUTHENTICATED` | 401 | missing or invalid bearer token |
| `FORBIDDEN` | 403 | wrong role |
| `DRAFT_NOT_EDITABLE` | 409 | save draft while a review is pending |
| `REVIEW_ALREADY_PENDING` | 409 | open a second review on the same draft |
| `REVIEWER_IS_AUTHOR` | 403 | approve/reject own description |
| `VERSION_NOT_APPROVED` | 409 | publish a version that has no approval |
| `PUBLICATION_BLOCKED` | 422 | publish with open gate items (`blocking[]`) |
| `PHOTO_FORMAT_UNSUPPORTED` | 422 | upload |
| `PHOTO_TOO_SMALL` | 422 | upload |
| `INVALID_DATE_RANGE` | 422 | price/discount `validTo` before `validFrom` |
| `DRAFT_NOT_FOUND` | 409 | request review when the product has no draft (D3/V4) |
| `REVIEW_NOT_PENDING` | 409 | approve or reject a review that is not pending (D5 re-decision) |
| `INTERNAL_ERROR` | 500 | unexpected failure |

## Decisions taken here

- **State is an enum on the resource**, not a separate sub-resource: `DRAFT · PENDING_REVIEW ·
  PUBLISHED · SCHEDULED · BLOCKED · REMOVED` on the product, `SCHEDULED · ACTIVE · EXPIRED`
  on a price, `PENDING · APPROVED · REJECTED` on a review request.
- **Publication is its own resource** (`/publications`) because a product has many published
  versions over time and a scheduled one that is not yet visible.
- **Completeness travels with the draft** (`completeness.complete` + `completeness.missing[]`)
  so the UI does not police the rules itself — this is property P1.
- **Revert creates a new draft** (`basedOnVersion`), it does not rewind history — property P4.
- **Removal is idempotent** (204 twice), because the mockup exposes it as a repeatable action.
- **Money is a string + currency** (`"259.00"` + `"PLN"`), never a float.
- **Two publication dates**: `availableFrom` is what was requested, `visibleFrom` is when the version actually becomes visible (element 07, D4).

## Open — blocking acceptance of this element

- **Q20** — the mockup-invented specifics this contract encodes: the mandatory-item set, the
  advisory grammar check, `PLN`, the `−10 %/−15 %` discount form, the state names, and
  "approve is allowed with missing items, publish is not". Contract asserts hinge on them.
- **Q18** — pricing units, currency and rounding; `percent` is a string here and
  `INVALID_DATE_RANGE` does not yet cover overlapping ranges.
- **R4** — the illegible board annotation is not used by any endpoint.
- **Q21** — photo constraints: `minWidth`/`minHeight` and accepted MIME types are invented.
  Confirm, or give the real format policy.
- **Q22** — is `GET /products` the UI's own list, or does the UI read the `browsing offer`
  surface? Currently assumed to be the offer-management (admin) surface — element 03 covers
  the other one.
- **F1** — the multipart fixtures (`prototypes/fixtures/photo.jpeg`, `tiny.png`, `scan.pdf`)
  do not exist yet; they are needed to run the photo error cases at implementation time.
- **Q23** — `GET /product-photo-formats` is invented to serve the "allowed formats" hint;
  it could be a build-time constant in the UI instead. Which?
