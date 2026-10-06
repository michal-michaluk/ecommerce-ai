# Frontend API — offer management
#
# The HTTP contract the offer-management UI calls. Executable with hurl:
#   hurl --test --variable BASE_URL=http://localhost:8080 \
#        --variable content_manager_token=... --variable sales_token=... \
#        prototypes/frontend-api.hurl
#
# Full response shapes for every entry: prototypes/frontend-api.responses.md
# Auth: OAuth2 / OIDC JWT bearer (blueprint docs/arch/microservice-java-spring/security.md).
# Deny by default; every endpoint below requires a bearer token.
# Roles: content-manager (products, drafts, photos, reviews, publication),
#        sales (prices and discounts).

# ══════════════════════════════════════════════════════════════════════════
# 01 Product catalog
# ══════════════════════════════════════════════════════════════════════════

# ── List products ─────────────────────────────────────────────────────────
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

# ── Unauthenticated ───────────────────────────────────────────────────────
GET {{BASE_URL}}/products
HTTP 401

# ── Wrong role ────────────────────────────────────────────────────────────
GET {{BASE_URL}}/products
Authorization: Bearer {{sales_token}}
HTTP 403
[Asserts]
jsonpath "$.code" == "FORBIDDEN"

# ══════════════════════════════════════════════════════════════════════════
# 02 Create product  → blank draft
# ══════════════════════════════════════════════════════════════════════════

# ── Create ────────────────────────────────────────────────────────────────
POST {{BASE_URL}}/products
Authorization: Bearer {{content_manager_token}}
Content-Type: application/json
{
  "title": "Prosto z półki / Kosiarka ręczna 340",
  "category": "Ogród"
}
HTTP 201
[Asserts]
jsonpath "$.productId" exists
jsonpath "$.state" == "DRAFT"
jsonpath "$.descriptionVersion" == "v1"

# ── Title is mandatory ────────────────────────────────────────────────────
POST {{BASE_URL}}/products
Authorization: Bearer {{content_manager_token}}
Content-Type: application/json
{ "title": "", "category": "Ogród" }
HTTP 422
[Asserts]
jsonpath "$.code" == "VALIDATION_FAILED"
jsonpath "$.details.fields" contains "title"

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

# ── Draft not editable while under review ─────────────────────────────────
PUT {{BASE_URL}}/products/{{product_id}}/description-draft
Authorization: Bearer {{content_manager_token}}
Content-Type: application/json
{ "title": "changed", "description": "changed" }
HTTP 409
[Asserts]
jsonpath "$.code" == "DRAFT_NOT_EDITABLE"

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
file: file,prototypes/fixtures/photo.jpeg; image/jpeg
HTTP 201
[Asserts]
jsonpath "$.photoId" exists
jsonpath "$.fileName" exists
jsonpath "$.width" isInteger
jsonpath "$.height" isInteger

# ── Photo below the minimum size ──────────────────────────────────────────
POST {{BASE_URL}}/products/{{product_id}}/description-draft/photos
Authorization: Bearer {{content_manager_token}}
[MultipartFormData]
file: file,prototypes/fixtures/tiny.png; image/png
HTTP 422
[Asserts]
jsonpath "$.code" == "PHOTO_TOO_SMALL"

# ── Unsupported format ────────────────────────────────────────────────────
POST {{BASE_URL}}/products/{{product_id}}/description-draft/photos
Authorization: Bearer {{content_manager_token}}
[MultipartFormData]
file: file,prototypes/fixtures/scan.pdf; application/pdf
HTTP 422
[Asserts]
jsonpath "$.code" == "PHOTO_FORMAT_UNSUPPORTED"

# ── Remove a photo ────────────────────────────────────────────────────────
DELETE {{BASE_URL}}/products/{{product_id}}/description-draft/photos/{{photo_id}}
Authorization: Bearer {{content_manager_token}}
HTTP 204

# ══════════════════════════════════════════════════════════════════════════
# 05 Review queue
# ══════════════════════════════════════════════════════════════════════════

# ── Request a review ──────────────────────────────────────────────────────
POST {{BASE_URL}}/products/{{product_id}}/description-draft/review-requests
Authorization: Bearer {{content_manager_token}}
HTTP 201
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
Authorization: Bearer {{content_manager_token}}
HTTP 200
[Asserts]
jsonpath "$.status" == "APPROVED"
jsonpath "$.approvedBy" exists

# ── Reject ────────────────────────────────────────────────────────────────
POST {{BASE_URL}}/review-requests/{{review_request_id}}/rejection
Authorization: Bearer {{content_manager_token}}
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

# ── Publish a version, scheduled for a future availability date ───────────
POST {{BASE_URL}}/products/{{product_id}}/publications
Authorization: Bearer {{content_manager_token}}
Content-Type: application/json
{
  "descriptionVersion": "v3",
  "availableFrom": "2019-07-01"
}
HTTP 201
[Asserts]
jsonpath "$.publicationId" exists
jsonpath "$.descriptionVersion" == "v3"
jsonpath "$.state" == "SCHEDULED"
jsonpath "$.visibleFrom" == "2019-07-01"

# ── Publish immediately ───────────────────────────────────────────────────
POST {{BASE_URL}}/products/{{product_id}}/publications
Authorization: Bearer {{content_manager_token}}
Content-Type: application/json
{ "descriptionVersion": "v3", "availableFrom": null }
HTTP 201
[Asserts]
jsonpath "$.state" == "PUBLISHED"

# ── Publication blocked by the quality gate ───────────────────────────────
POST {{BASE_URL}}/products/p-with-issues/publications
Authorization: Bearer {{content_manager_token}}
Content-Type: application/json
{ "descriptionVersion": "v1", "availableFrom": null }
HTTP 422
[Asserts]
jsonpath "$.code" == "PUBLICATION_BLOCKED"
jsonpath "$.details.blocking" isCollection

# ── Unapproved version cannot be published ────────────────────────────────
POST {{BASE_URL}}/products/{{product_id}}/publications
Authorization: Bearer {{content_manager_token}}
Content-Type: application/json
{ "descriptionVersion": "v4", "availableFrom": null }
HTTP 409
[Asserts]
jsonpath "$.code" == "VERSION_NOT_APPROVED"

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

# ── Schedule a price ──────────────────────────────────────────────────────
POST {{BASE_URL}}/products/{{product_id}}/prices
Authorization: Bearer {{sales_token}}
Content-Type: application/json
{
  "kind": "PRICE",
  "amount": { "value": "259.00", "currency": "PLN" },
  "validFrom": "2019-07-01",
  "validTo": null
}
HTTP 201
[Asserts]
jsonpath "$.priceId" exists
jsonpath "$.state" == "SCHEDULED"

# ── Schedule a discount ───────────────────────────────────────────────────
POST {{BASE_URL}}/products/{{product_id}}/prices
Authorization: Bearer {{sales_token}}
Content-Type: application/json
{
  "kind": "DISCOUNT",
  "percent": "10",
  "validFrom": "2019-07-01",
  "validTo": "2019-07-31"
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
  "validFrom": "2019-08-01",
  "validTo": "2019-07-01"
}
HTTP 422
[Asserts]
jsonpath "$.code" == "INVALID_DATE_RANGE"

# ── Content manager may not set prices ────────────────────────────────────
POST {{BASE_URL}}/products/{{product_id}}/prices
Authorization: Bearer {{content_manager_token}}
Content-Type: application/json
{ "kind": "PRICE", "amount": { "value": "1.00", "currency": "PLN" }, "validFrom": "2019-07-01" }
HTTP 403
[Asserts]
jsonpath "$.code" == "FORBIDDEN"

# ── Change a scheduled entry ──────────────────────────────────────────────
PUT {{BASE_URL}}/products/{{product_id}}/prices/{{price_id}}
Authorization: Bearer {{sales_token}}
Content-Type: application/json
{ "amount": { "value": "269.00", "currency": "PLN" }, "validFrom": "2019-07-01", "validTo": null }
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
POST {{BASE_URL}}/products/{{product_id}}/versions/v2/revert
Authorization: Bearer {{content_manager_token}}
HTTP 201
[Asserts]
jsonpath "$.version" exists
jsonpath "$.state" == "EDITING"
jsonpath "$.basedOnVersion" == "v2"

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
