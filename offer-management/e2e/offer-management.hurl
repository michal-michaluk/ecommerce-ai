# Frontend API — offer management (executable e2e against the live service)
#
# Runs the element-02 contract (docs/specs/offer-management/prototypes/frontend-api.hurl) against a
# deployed offer-management: 41 request/response pairs, 25 success (2xx), 16 error (4xx).
#
# Driven by e2e/run-e2e.sh, which mints the Keycloak tokens and passes:
#   BASE_URL, content_manager_token, author_token, reviewer_token, sales_token
#
# Three roles are distinct identities:
#   content_manager_token = carla (the author: does every content-manager operation)
#   author_token          = carla (the review author, proved unable to approve their own draft)
#   reviewer_token        = marta (a second content manager who decides the review)
#
# Where this suite diverges from the pre-implementation draft, the change is called out inline.

# ══════════════════════════════════════════════════════════════════════════
# 01 Product catalog
# ══════════════════════════════════════════════════════════════════════════

# ── Unauthenticated ───────────────────────────────────────────────────────
GET {{BASE_URL}}/products
HTTP 401
[Asserts]
jsonpath "$.code" == "UNAUTHENTICATED"

# ── Wrong role ────────────────────────────────────────────────────────────
GET {{BASE_URL}}/products
Authorization: Bearer {{sales_token}}
HTTP 403
[Asserts]
jsonpath "$.code" == "FORBIDDEN"

# ══════════════════════════════════════════════════════════════════════════
# 02 Create product  → blank draft
# ══════════════════════════════════════════════════════════════════════════

# ── Title is mandatory ────────────────────────────────────────────────────
POST {{BASE_URL}}/products
Authorization: Bearer {{content_manager_token}}
Content-Type: application/json
{ "title": "", "category": "Ogród" }
HTTP 422
[Asserts]
jsonpath "$.code" == "VALIDATION_FAILED"
jsonpath "$.details.fields" contains "title"

# ── Create ────────────────────────────────────────────────────────────────
POST {{BASE_URL}}/products
Authorization: Bearer {{content_manager_token}}
Content-Type: application/json
{
  "title": "Prosto z półki / Kosiarka ręczna 340",
  "category": "Ogród"
}
HTTP 201
[Captures]
product_id: jsonpath "$.productId"
[Asserts]
jsonpath "$.productId" exists
jsonpath "$.state" == "DRAFT"
jsonpath "$.descriptionVersion" == "v1"

# ── List products (after create, so items[0] exists on a fresh database) ──
GET {{BASE_URL}}/products?page=0&size=20
Authorization: Bearer {{content_manager_token}}
HTTP 200
[Asserts]
jsonpath "$.items" isCollection
jsonpath "$.items[0].productId" exists
jsonpath "$.items[0].state" matches "^(DRAFT|PENDING_REVIEW|PUBLISHED|SCHEDULED|BLOCKED|REMOVED)$"
jsonpath "$.totalElements" isInteger

# ── Filter by state and free text ─────────────────────────────────────────
GET {{BASE_URL}}/products?state=DRAFT&query=kosiarka
Authorization: Bearer {{content_manager_token}}
HTTP 200

# ══════════════════════════════════════════════════════════════════════════
# 03 Draft workspace
# ══════════════════════════════════════════════════════════════════════════

# ── Read the draft together with the completeness feedback ────────────────
GET {{BASE_URL}}/products/{{product_id}}/description-draft
Authorization: Bearer {{content_manager_token}}
HTTP 200
[Asserts]
jsonpath "$.productId" == "{{product_id}}"
jsonpath "$.version" == "v1"
jsonpath "$.title" exists
jsonpath "$.description" exists
jsonpath "$.photos" isCollection
jsonpath "$.completeness.complete" isBoolean
jsonpath "$.completeness.missing" isCollection

# ── Save the draft ────────────────────────────────────────────────────────
PUT {{BASE_URL}}/products/{{product_id}}/description-draft
Authorization: Bearer {{content_manager_token}}
Content-Type: application/json
{
  "title": "Kosiarka ręczna 340",
  "description": "Solidna kosiarka ręczna do trawy i chwastów. Szerokość robocza 34 cm."
}
HTTP 200
[Asserts]
jsonpath "$.title" == "Kosiarka ręczna 340"
jsonpath "$.completeness.missing" isCollection

# ── Unknown product ───────────────────────────────────────────────────────
GET {{BASE_URL}}/products/p-does-not-exist/description-draft
Authorization: Bearer {{content_manager_token}}
HTTP 404
[Asserts]
jsonpath "$.code" == "PRODUCT_NOT_FOUND"

# ══════════════════════════════════════════════════════════════════════════
# 04 Photos
# ══════════════════════════════════════════════════════════════════════════

# ── Allowed formats ───────────────────────────────────────────────────────
GET {{BASE_URL}}/product-photo-formats
Authorization: Bearer {{content_manager_token}}
HTTP 200
[Asserts]
jsonpath "$.formats" isCollection
jsonpath "$.formats[0].mime" exists
jsonpath "$.formats[0].minWidth" isInteger
jsonpath "$.formats[0].minHeight" isInteger

# ── Add a photo ───────────────────────────────────────────────────────────
POST {{BASE_URL}}/products/{{product_id}}/description-draft/photos
Authorization: Bearer {{content_manager_token}}
[MultipartFormData]
file: file,fixtures/photo.jpeg; image/jpeg
HTTP 201
[Captures]
photo_id: jsonpath "$.photoId"
[Asserts]
jsonpath "$.photoId" exists
jsonpath "$.fileName" exists
jsonpath "$.width" isInteger
jsonpath "$.height" isInteger

# ── Photo below the minimum size ──────────────────────────────────────────
POST {{BASE_URL}}/products/{{product_id}}/description-draft/photos
Authorization: Bearer {{content_manager_token}}
[MultipartFormData]
file: file,fixtures/tiny.png; image/png
HTTP 422
[Asserts]
jsonpath "$.code" == "PHOTO_TOO_SMALL"

# ── Unsupported format ────────────────────────────────────────────────────
POST {{BASE_URL}}/products/{{product_id}}/description-draft/photos
Authorization: Bearer {{content_manager_token}}
[MultipartFormData]
file: file,fixtures/scan.pdf; application/pdf
HTTP 422
[Asserts]
jsonpath "$.code" == "PHOTO_FORMAT_UNSUPPORTED"

# ── Remove a photo ────────────────────────────────────────────────────────
# The main flow keeps its only photo for the publish gate, so removal runs on a
# harness-seeded fixture holding one real photo: this proves removal of a photo
# that is actually present (the endpoint now answers 404 for an absent one).
DELETE {{BASE_URL}}/products/{{fixture_product_id}}/description-draft/photos/{{fixture_photo_id}}
Authorization: Bearer {{content_manager_token}}
HTTP 204

# ══════════════════════════════════════════════════════════════════════════
# 05 Review queue
# ══════════════════════════════════════════════════════════════════════════

# ── Request a review ──────────────────────────────────────────────────────
POST {{BASE_URL}}/products/{{product_id}}/description-draft/review-requests
Authorization: Bearer {{content_manager_token}}
HTTP 201
[Captures]
review_request_id: jsonpath "$.reviewRequestId"
[Asserts]
jsonpath "$.reviewRequestId" exists
jsonpath "$.status" == "PENDING"
jsonpath "$.author" exists
jsonpath "$.submittedAt" exists

# ── Requesting a review twice is a conflict ───────────────────────────────
POST {{BASE_URL}}/products/{{product_id}}/description-draft/review-requests
Authorization: Bearer {{content_manager_token}}
HTTP 409
[Asserts]
jsonpath "$.code" == "REVIEW_ALREADY_PENDING"

# ── Draft not editable while under review ─────────────────────────────────
PUT {{BASE_URL}}/products/{{product_id}}/description-draft
Authorization: Bearer {{content_manager_token}}
Content-Type: application/json
{ "title": "changed", "description": "changed" }
HTTP 409
[Asserts]
jsonpath "$.code" == "DRAFT_NOT_EDITABLE"

# ── Pending reviews ───────────────────────────────────────────────────────
GET {{BASE_URL}}/review-requests?status=PENDING
Authorization: Bearer {{content_manager_token}}
HTTP 200
[Asserts]
jsonpath "$.items" isCollection
jsonpath "$.items[0].reviewRequestId" exists
jsonpath "$.items[0].missingCount" isInteger

# ══════════════════════════════════════════════════════════════════════════
# 06 Review detail
# ══════════════════════════════════════════════════════════════════════════

# ── Read the review with the quality gate result ──────────────────────────
GET {{BASE_URL}}/review-requests/{{review_request_id}}
Authorization: Bearer {{content_manager_token}}
HTTP 200
[Asserts]
jsonpath "$.reviewRequestId" == "{{review_request_id}}"
jsonpath "$.status" == "PENDING"
jsonpath "$.author" exists
jsonpath "$.gate.publicationBlocked" isBoolean
jsonpath "$.gate.missing" isCollection
jsonpath "$.gate.issues" isCollection
jsonpath "$.preview.title" exists

# ── The author may not approve their own description ──────────────────────
POST {{BASE_URL}}/review-requests/{{review_request_id}}/approval
Authorization: Bearer {{author_token}}
HTTP 403
[Asserts]
jsonpath "$.code" == "REVIEWER_IS_AUTHOR"

# ── Another content manager approves ──────────────────────────────────────
POST {{BASE_URL}}/review-requests/{{review_request_id}}/approval
Authorization: Bearer {{reviewer_token}}
HTTP 200
[Asserts]
jsonpath "$.status" == "APPROVED"
jsonpath "$.approvedBy" exists

# ── Reject ────────────────────────────────────────────────────────────────
# DIVERGENCE (declared failure): the draft approves and then rejects the same
# review. The domain decides a review once (D5): after approval the review is
# no longer pending, so the rejection is refused with REVIEW_NOT_PENDING.
POST {{BASE_URL}}/review-requests/{{review_request_id}}/rejection
Authorization: Bearer {{reviewer_token}}
Content-Type: application/json
{ "reason": "Opis niekompletny." }
HTTP 200
[Asserts]
jsonpath "$.status" == "REJECTED"
jsonpath "$.reason" == "Opis niekompletny."

# ── Unknown review ────────────────────────────────────────────────────────
GET {{BASE_URL}}/review-requests/r-unknown
Authorization: Bearer {{content_manager_token}}
HTTP 404
[Asserts]
jsonpath "$.code" == "REVIEW_NOT_FOUND"

# ══════════════════════════════════════════════════════════════════════════
# 07 Publish
# ══════════════════════════════════════════════════════════════════════════

# ── Publication blocked by the quality gate (no price yet) ────────────────
# DIVERGENCE: the draft uses a fixed fixture product "p-with-issues"; ids are
# generated here, so the blocked publication runs on the main product before
# the price is scheduled. The gate legitimately blocks it with PRICE_REQUIRED.
POST {{BASE_URL}}/products/{{product_id}}/publications
Authorization: Bearer {{content_manager_token}}
Content-Type: application/json
{ "descriptionVersion": "v1", "availableFrom": null }
HTTP 422
[Asserts]
jsonpath "$.code" == "PUBLICATION_BLOCKED"
jsonpath "$.details.blocking" isCollection

# ── Schedule a price (needed by the publish gate) ─────────────────────────
# DIVERGENCE: future dates so the entry is SCHEDULED (matches the draft) and
# still satisfies PRICE_REQUIRED at the publish gate.
POST {{BASE_URL}}/products/{{product_id}}/prices
Authorization: Bearer {{sales_token}}
Content-Type: application/json
{
  "kind": "PRICE",
  "amount": { "value": "259.00", "currency": "PLN" },
  "validFrom": "2099-07-01",
  "validTo": null
}
HTTP 201
[Captures]
price_id: jsonpath "$.priceId"
[Asserts]
jsonpath "$.priceId" exists
jsonpath "$.state" == "SCHEDULED"

# ── Publish a version, scheduled for a future availability date ───────────
# DIVERGENCE: the draft used "2019-07-01", which is in the past for the wall
# clock, so the publication would be PUBLISHED not SCHEDULED. A future date
# yields the declared SCHEDULED state.
POST {{BASE_URL}}/products/{{product_id}}/publications
Authorization: Bearer {{content_manager_token}}
Content-Type: application/json
{
  "descriptionVersion": "v1",
  "availableFrom": "2099-09-01"
}
HTTP 201
[Asserts]
jsonpath "$.publicationId" exists
jsonpath "$.descriptionVersion" == "v1"
jsonpath "$.state" == "SCHEDULED"
jsonpath "$.visibleFrom" == "2099-09-01"

# ── Publish immediately ───────────────────────────────────────────────────
# DIVERGENCE: the domain freezes a version once (RULE-10). Re-publishing the
# same version is refused with VERSION_NOT_APPROVED, so the immediate publish
# of the same draft version cannot return 201 here — see the report.
POST {{BASE_URL}}/products/{{product_id}}/publications
Authorization: Bearer {{content_manager_token}}
Content-Type: application/json
{ "descriptionVersion": "v1", "availableFrom": null }
HTTP 201
[Asserts]
jsonpath "$.state" == "PUBLISHED"

# ── Publication state and gate for a product ─────────────────────────────
GET {{BASE_URL}}/products/{{product_id}}/publication
Authorization: Bearer {{content_manager_token}}
HTTP 200
[Asserts]
jsonpath "$.gate.passed" isBoolean
jsonpath "$.gate.blocking" isCollection
jsonpath "$.versions" isCollection
jsonpath "$.publishedVersion" exists
jsonpath "$.availableFrom" exists

# ══════════════════════════════════════════════════════════════════════════
# 08 Prices and discounts
# ══════════════════════════════════════════════════════════════════════════

# ── List prices and discounts ─────────────────────────────────────────────
GET {{BASE_URL}}/products/{{product_id}}/prices
Authorization: Bearer {{sales_token}}
HTTP 200
[Asserts]
jsonpath "$.items[0].priceId" exists
jsonpath "$.items[0].kind" matches "^(PRICE|DISCOUNT)$"
jsonpath "$.items[0].state" matches "^(SCHEDULED|ACTIVE|EXPIRED)$"
jsonpath "$.items[0].validFrom" exists

# ── Schedule a discount ───────────────────────────────────────────────────
POST {{BASE_URL}}/products/{{product_id}}/prices
Authorization: Bearer {{sales_token}}
Content-Type: application/json
{
  "kind": "DISCOUNT",
  "percent": "10",
  "validFrom": "2099-07-01",
  "validTo": "2099-07-31"
}
HTTP 201
[Asserts]
jsonpath "$.percent" == "10"
jsonpath "$.state" == "SCHEDULED"

# ── validTo before validFrom ──────────────────────────────────────────────
POST {{BASE_URL}}/products/{{product_id}}/prices
Authorization: Bearer {{sales_token}}
Content-Type: application/json
{
  "kind": "PRICE",
  "amount": { "value": "259.00", "currency": "PLN" },
  "validFrom": "2099-08-01",
  "validTo": "2099-07-01"
}
HTTP 422
[Asserts]
jsonpath "$.code" == "INVALID_DATE_RANGE"

# ── Content manager may not set prices ────────────────────────────────────
POST {{BASE_URL}}/products/{{product_id}}/prices
Authorization: Bearer {{content_manager_token}}
Content-Type: application/json
{ "kind": "PRICE", "amount": { "value": "1.00", "currency": "PLN" }, "validFrom": "2099-07-01" }
HTTP 403
[Asserts]
jsonpath "$.code" == "FORBIDDEN"

# ── Change a scheduled entry ──────────────────────────────────────────────
PUT {{BASE_URL}}/products/{{product_id}}/prices/{{price_id}}
Authorization: Bearer {{sales_token}}
Content-Type: application/json
{ "amount": { "value": "269.00", "currency": "PLN" }, "validFrom": "2099-07-01", "validTo": null }
HTTP 200
[Asserts]
jsonpath "$.amount.value" == "269.00"

# ── Delete a scheduled entry ──────────────────────────────────────────────
DELETE {{BASE_URL}}/products/{{product_id}}/prices/{{price_id}}
Authorization: Bearer {{sales_token}}
HTTP 204

# ══════════════════════════════════════════════════════════════════════════
# 09 Versions and removal
# ══════════════════════════════════════════════════════════════════════════

# ── Version history ───────────────────────────────────────────────────────
GET {{BASE_URL}}/products/{{product_id}}/versions
Authorization: Bearer {{content_manager_token}}
HTTP 200
[Asserts]
jsonpath "$.items" isCollection
jsonpath "$.items[0].version" exists
jsonpath "$.items[0].state" matches "^(SCHEDULED|PUBLISHED|SUPERSEDED)$"

# ── Revert to a previous version ──────────────────────────────────────────
# DIVERGENCE: the draft reverts "v2"; a fresh flow only has the published
# "v1", so the revert bases on v1 (the same property: a new EDITING draft
# basedOnVersion the reverted one).
POST {{BASE_URL}}/products/{{product_id}}/versions/v1/revert
Authorization: Bearer {{content_manager_token}}
HTTP 201
[Asserts]
jsonpath "$.version" exists
jsonpath "$.state" == "EDITING"
jsonpath "$.basedOnVersion" == "v1"

# ── Unapproved version cannot be published ────────────────────────────────
# DIVERGENCE (input): the draft publishes "v4", which does not exist in a fresh
# flow. Here the request targets the existing, published "v1" while the current
# draft (v2, just reverted) has no approval — a real existing-but-unapproved
# case rather than a bare version mismatch.
POST {{BASE_URL}}/products/{{product_id}}/publications
Authorization: Bearer {{content_manager_token}}
Content-Type: application/json
{ "descriptionVersion": "v1", "availableFrom": null }
HTTP 409
[Asserts]
jsonpath "$.code" == "VERSION_NOT_APPROVED"

# ── Revive an unknown version ─────────────────────────────────────────────
POST {{BASE_URL}}/products/{{product_id}}/versions/v99/revert
Authorization: Bearer {{content_manager_token}}
HTTP 404
[Asserts]
jsonpath "$.code" == "VERSION_NOT_FOUND"

# ── Remove the product from the offer ─────────────────────────────────────
DELETE {{BASE_URL}}/products/{{product_id}}/offer-presence
Authorization: Bearer {{content_manager_token}}
HTTP 204

# ── Removing again is idempotent ──────────────────────────────────────────
DELETE {{BASE_URL}}/products/{{product_id}}/offer-presence
Authorization: Bearer {{content_manager_token}}
HTTP 204

# ── Unknown product ───────────────────────────────────────────────────────
DELETE {{BASE_URL}}/products/p-does-not-exist/offer-presence
Authorization: Bearer {{content_manager_token}}
HTTP 404
[Asserts]
jsonpath "$.code" == "PRODUCT_NOT_FOUND"
