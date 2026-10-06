# Frontend API — complete response shapes

Every response of [`frontend-api.hurl`](frontend-api.hurl), with all fields. These are
**example shapes**, not executed; the `.hurl` file holds the executable assertions.

Conventions:

- Money is `{ "value": "259.00", "currency": "PLN" }` — a decimal string, never a float.
- Instant is ISO-8601 UTC: `"2019-10-02T09:14:00Z"`. Date is `"2019-07-01"`.
- Absent optional value is `null`, never omitted.
- `ErrorBody` is shared by every 4xx/5xx.

---

## ErrorBody

```json
{
  "code": "PRODUCT_NOT_FOUND",
  "message": "Product p-2019-0442 does not exist."
}
```

The platform fallback (no resource of its own behind the request):

```json
{
  "code": "NOT_FOUND",
  "message": "No resource matches the request."
}
```

The overlap conflict of the pricing context (RULE-25, A5):

```json
{
  "code": "PRICE_OVERLAP",
  "message": "The price range overlaps an existing entry."
}
```

Two codes carry `details`:

```json
{
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed.",
  "details": { "fields": ["title"] }
}
```

```json
{
  "code": "PUBLICATION_BLOCKED",
  "message": "The description cannot be published while the quality gate reports items.",
  "details": {
    "blocking": [
      { "code": "PRICE_REQUIRED", "label": "Price valid for a date range" }
    ]
  }
}
```

| Status | `code` values |
|---|---|
| 401 | `UNAUTHENTICATED` |
| 403 | `FORBIDDEN`, `REVIEWER_IS_AUTHOR` |
| 404 | `PRODUCT_NOT_FOUND`, `REVIEW_NOT_FOUND`, `VERSION_NOT_FOUND`, `NOT_FOUND` |
| 409 | `DRAFT_NOT_EDITABLE`, `REVIEW_ALREADY_PENDING`, `REVIEW_NOT_PENDING`, `VERSION_NOT_APPROVED`, `PRICE_OVERLAP` |
| 422 | `VALIDATION_FAILED`, `PUBLICATION_BLOCKED`, `PHOTO_FORMAT_UNSUPPORTED`, `PHOTO_TOO_SMALL`, `INVALID_DATE_RANGE` |
| 500 | `INTERNAL_ERROR` |

---

## Group A — Product

### `GET /products` → 200 `PagedProducts`

```json
{
  "items": [
    {
      "productId": "p-2019-0442",
      "title": "Kosiarka ręczna 340",
      "category": "Ogród",
      "state": "PUBLISHED",
      "descriptionVersion": "v3",
      "publishedVersion": "v3",
      "availableFrom": "2019-07-01",
      "activePrice": { "value": "259.00", "currency": "PLN" },
      "activeDiscountPercent": "10",
      "photoCount": 2,
      "updatedAt": "2019-10-02T09:14:00Z",
      "updatedBy": "a.kowalska"
    },
    {
      "productId": "p-2019-0601",
      "title": "Miotła ogrodowa 120",
      "category": "Ogród",
      "state": "PENDING_REVIEW",
      "descriptionVersion": "v1",
      "publishedVersion": null,
      "availableFrom": null,
      "activePrice": null,
      "activeDiscountPercent": null,
      "photoCount": 3,
      "updatedAt": "2019-10-03T11:02:00Z",
      "updatedBy": "a.kowalska"
    },
    {
      "productId": "p-2019-0510",
      "title": "Stół piknikowy",
      "category": "Ogród",
      "state": "BLOCKED",
      "descriptionVersion": "v1",
      "publishedVersion": null,
      "availableFrom": null,
      "activePrice": null,
      "activeDiscountPercent": null,
      "photoCount": 2,
      "updatedAt": "2019-10-04T16:40:00Z",
      "updatedBy": "a.kowalska"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 3,
  "totalPages": 1
}
```

Each row satisfies the `OfferState` precedence (element 04 §10): `p-2019-0442` has a visible
version, `p-2019-0601` has a draft in review, `p-2019-0510` has an approved version the publish
guard refuses.

`productId` in a URL that was not requested is not a real id — see `GET /products/{productId}`.

### `GET /products/{productId}` → 200 `Product`

```json
{
  "productId": "p-2019-0442",
  "title": "Kosiarka ręczna 340",
  "category": "Ogród",
  "state": "PUBLISHED",
  "descriptionVersion": "v3",
  "publishedVersion": "v3",
  "availableFrom": "2019-07-01",
  "activePrice": { "value": "259.00", "currency": "PLN" },
  "activeDiscountPercent": "10",
  "photoCount": 2,
  "createdAt": "2019-03-11T10:00:00Z",
  "updatedAt": "2019-10-02T09:14:00Z"
}
```

### `POST /products` → 201 `Product`

```json
{
  "productId": "p-2019-0442",
  "title": "Prosto z półki / Kosiarka ręczna 340",
  "category": "Ogród",
  "state": "DRAFT",
  "descriptionVersion": "v1",
  "publishedVersion": null,
  "availableFrom": null,
  "activePrice": null,
  "activeDiscountPercent": null,
  "photoCount": 0,
  "createdAt": "2019-10-05T09:54:47Z",
  "updatedAt": "2019-10-05T09:54:47Z"
}
```

---

## Group B — Description draft

### `GET /products/{productId}/description-draft` → 200 `DescriptionDraft`

```json
{
  "productId": "p-2019-0442",
  "version": "v3",
  "state": "EDITING",
  "title": "Prosto z półki / Kosiarka ręczna 340",
  "description": "Solidna kosiarka ręczna do trawy i chwastów. Szerokość robocza 34 cm, wysokość cięcia regulowana w 4 stopniach.",
  "attributes": {
    "category": "Ogród",
    "manualUrl": null
  },
  "photos": [
    {
      "photoId": "ph-1",
      "fileName": "kosiarka-01.jpg",
      "mime": "image/jpeg",
      "width": 1200,
      "height": 1200,
      "sizeBytes": 184320,
      "position": 0,
      "uploadedAt": "2019-10-01T12:20:00Z"
    },
    {
      "photoId": "ph-2",
      "fileName": "kosiarka-02.jpg",
      "mime": "image/jpeg",
      "width": 1200,
      "height": 1200,
      "sizeBytes": 176128,
      "position": 1,
      "uploadedAt": "2019-10-01T12:21:00Z"
    }
  ],
  "completeness": {
    "complete": false,
    "missing": [
      { "code": "PRICE_REQUIRED", "label": "Price valid for a date range" }
    ]
  },
  "lastSavedAt": "2019-10-02T09:10:00Z",
  "updatedBy": "a.kowalska",
  "reviewRequestId": null
}
```

`reviewRequestId` is non-null while a review is pending; it is `null` for a free draft, for a
rejected draft and for an approved-but-unpublished draft.

### `PUT /products/{productId}/description-draft` → 200 `DescriptionDraft`

Same shape as above.

---

## Group C — Photos

### `GET /product-photo-formats` → 200 `PhotoFormats`

```json
{
  "formats": [
    {
      "mime": "image/jpeg",
      "extensions": ["jpg", "jpeg"],
      "minWidth": 1000,
      "minHeight": 1000,
      "maxBytes": 10485760
    },
    {
      "mime": "image/png",
      "extensions": ["png"],
      "minWidth": 1000,
      "minHeight": 1000,
      "maxBytes": 10485760
    }
  ]
}
```

### `POST /products/{productId}/description-draft/photos` → 201 `Photo`

```json
{
  "photoId": "ph-3",
  "fileName": "photo.jpeg",
  "mime": "image/jpeg",
  "width": 1200,
  "height": 1200,
  "sizeBytes": 184320,
  "position": 2,
  "uploadedAt": "2019-10-05T10:01:00Z"
}
```

### `DELETE /products/{productId}/description-draft/photos/{photoId}` → 204

No body.

---

## Group D — Review

### `POST /products/{productId}/description-draft/review-requests` → 201 `ReviewRequest`

```json
{
  "reviewRequestId": "rr-1",
  "productId": "p-2019-0442",
  "descriptionVersion": "v3",
  "status": "PENDING",
  "author": "a.kowalska",
  "submittedAt": "2019-10-02T09:14:00Z",
  "missingCount": 1
}
```

### `GET /review-requests?status=PENDING` → 200 `PagedReviewRequests`

```json
{
  "items": [
    {
      "reviewRequestId": "rr-1",
      "productId": "p-2019-0442",
      "productTitle": "Prosto z półki / Kosiarka ręczna 340",
      "descriptionVersion": "v3",
      "status": "PENDING",
      "author": "a.kowalska",
      "submittedAt": "2019-10-02T09:14:00Z",
      "missingCount": 1
    },
    {
      "reviewRequestId": "rr-2",
      "productId": "p-2019-0601",
      "productTitle": "Miotła ogrodowa 120",
      "descriptionVersion": "v1",
      "status": "PENDING",
      "author": "a.kowalska",
      "submittedAt": "2019-10-03T11:02:00Z",
      "missingCount": 0
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 2,
  "totalPages": 1
}
```

### `GET /review-requests/{reviewRequestId}` → 200 `ReviewDetail`

```json
{
  "reviewRequestId": "rr-1",
  "productId": "p-2019-0442",
  "descriptionVersion": "v3",
  "status": "PENDING",
  "author": "a.kowalska",
  "submittedAt": "2019-10-02T09:14:00Z",
  "gate": {
    "publicationBlocked": true,
    "missing": [
      { "code": "PRICE_REQUIRED", "label": "Price valid for a date range" }
    ],
    "issues": [
      {
        "severity": "ADVISORY",
        "code": "GRAMMAR",
        "message": "Sentence 3 looks incomplete: \"wysokość cięcia regulowana w 4 stopniach.\""
      }
    ]
  },
  "preview": {
    "title": "Prosto z półki / Kosiarka ręczna 340",
    "description": "Solidna kosiarka ręczna do trawy i chwastów. Szerokość robocza 34 cm.",
    "wordCount": 142,
    "photoCount": 2,
    "price": null
  },
  "decision": null
}
```

After a decision:

```json
{
  "decision": {
    "outcome": "REJECTED",
    "decidedBy": "m.nowak",
    "decidedAt": "2019-10-05T10:20:00Z",
    "reason": "Opis niekompletny."
  }
}
```

`severity` ∈ `ADVISORY` (never blocks) — a blocking item is reported in `missing`, not in `issues`.

### `POST /review-requests/{reviewRequestId}/approval` → 200 `ReviewDecision`

```json
{
  "reviewRequestId": "rr-1",
  "status": "APPROVED",
  "approvedBy": "m.nowak",
  "decidedAt": "2019-10-05T10:20:00Z"
}
```

### `POST /review-requests/{reviewRequestId}/rejection` → 200 `ReviewDecision`

```json
{
  "reviewRequestId": "rr-1",
  "status": "REJECTED",
  "rejectedBy": "m.nowak",
  "reason": "Opis niekompletny.",
  "decidedAt": "2019-10-05T10:20:00Z"
}
```

---

## Group E — Publication

### `GET /products/{productId}/publication` → 200 `PublicationState`

```json
{
  "productId": "p-2019-0442",
  "gate": { "passed": true, "blocking": [] },
  "publishedVersion": "v3",
  "availableFrom": "2019-07-01",
  "versions": [
    {
      "version": "v3",
      "state": "SCHEDULED",
      "availableFrom": "2019-07-01",
      "publishedAt": null
    },
    {
      "version": "v2",
      "state": "PUBLISHED",
      "availableFrom": "2019-05-02",
      "publishedAt": "2019-05-02T10:00:00Z"
    },
    {
      "version": "v1",
      "state": "SUPERSEDED",
      "availableFrom": "2019-03-11",
      "publishedAt": "2019-03-11T10:00:00Z"
    }
  ]
}
```

With open items the same endpoint returns `"gate": { "passed": false, "blocking": [...] }`.

### `POST /products/{productId}/publications` → 201 `Publication`

```json
{
  "publicationId": "pub-1",
  "productId": "p-2019-0442",
  "descriptionVersion": "v3",
  "state": "SCHEDULED",
  "visibleFrom": "2019-07-01",
  "publishedAt": "2019-10-05T10:25:00Z"
}
```

Two dates, deliberately distinct:

- `availableFrom` — the date **requested** at publish time; `null` means "now".
- `visibleFrom` — the date the version **actually becomes** visible; equal to `availableFrom`
  when that is in the future, otherwise to `publishedAt` (element 07, D4/T1–T2).

Immediate publication (`availableFrom: null`) therefore returns `"state": "PUBLISHED"` with
`"visibleFrom"` equal to `publishedAt`.

---

## Group F — Prices

### `GET /products/{productId}/prices` → 200 `PriceList`

```json
{
  "productId": "p-2019-0442",
  "items": [
    {
      "priceId": "pr-1",
      "kind": "PRICE",
      "amount": { "value": "249.00", "currency": "PLN" },
      "percent": null,
      "validFrom": "2019-06-01",
      "validTo": "2019-06-30",
      "state": "EXPIRED"
    },
    {
      "priceId": "pr-2",
      "kind": "PRICE",
      "amount": { "value": "259.00", "currency": "PLN" },
      "percent": null,
      "validFrom": "2019-07-01",
      "validTo": null,
      "state": "SCHEDULED"
    },
    {
      "priceId": "pr-3",
      "kind": "DISCOUNT",
      "amount": null,
      "percent": "10",
      "validFrom": "2019-07-01",
      "validTo": "2019-07-31",
      "state": "SCHEDULED"
    }
  ]
}
```

Exactly one of `amount` / `percent` is non-null, selected by `kind`.

### `POST /products/{productId}/prices` → 201 `PriceEntry`

```json
{
  "priceId": "pr-4",
  "kind": "DISCOUNT",
  "amount": null,
  "percent": "10",
  "validFrom": "2019-07-01",
  "validTo": "2019-07-31",
  "state": "SCHEDULED"
}
```

### `PUT /products/{productId}/prices/{priceId}` → 200 `PriceEntry`

```json
{
  "priceId": "pr-4",
  "kind": "PRICE",
  "amount": { "value": "269.00", "currency": "PLN" },
  "percent": null,
  "validFrom": "2019-07-01",
  "validTo": null,
  "state": "SCHEDULED"
}
```

### `DELETE /products/{productId}/prices/{priceId}` → 204

No body.

---

## Group G — Versions and removal

### `GET /products/{productId}/versions` → 200 `VersionList`

```json
{
  "productId": "p-2019-0442",
  "items": [
    {
      "version": "v3",
      "state": "SCHEDULED",
      "availableFrom": "2019-07-01",
      "publishedAt": null,
      "basedOnVersion": "v2",
      "createdAt": "2019-06-20T08:00:00Z"
    },
    {
      "version": "v2",
      "state": "SUPERSEDED",
      "availableFrom": "2019-05-02",
      "publishedAt": "2019-05-02T10:00:00Z",
      "basedOnVersion": null,
      "createdAt": "2019-04-28T08:00:00Z"
    }
  ]
}
```

`state` ∈ `SCHEDULED · PUBLISHED · SUPERSEDED` — derived from the Publication (element 04 §5). `DRAFT` and `APPROVED` are draft states, never version states.

### `POST /products/{productId}/versions/{version}/revert` → 201 `DescriptionDraft`

```json
{
  "productId": "p-2019-0442",
  "version": "v4",
  "state": "EDITING",
  "basedOnVersion": "v2",
  "lastSavedAt": "2019-10-05T10:30:00Z",
  "updatedBy": "a.kowalska"
}
```

Revert copies an older version into a **new draft** (`basedOnVersion` set, `state: EDITING`) which then follows the normal review and publish path — it never creates a version directly and never rewrites history (RULE-12, property P4).

### `DELETE /products/{productId}/offer-presence` → 204

No body. Idempotent: 204 whether the product was published or already removed.

---

## Open

These shapes encode details that are **not** in the materials — see the registers in
[`../spec.md`](../spec.md): **Q20** (mandatory-item set, advisory grammar check, PLN, the
`−10 %/−15 %` form, state names), **Q18** (pricing units, currency, rounding, overlapping
ranges), **Q21** (accepted MIME types and minimum dimensions), **Q23**
(`GET /product-photo-formats` as an endpoint).
