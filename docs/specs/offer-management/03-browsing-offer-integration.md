# Element 03 — `offer management` ↔ `browsing offer`

How this context hands off to the customer-facing shop.

## Direction

**Outbound only.** This context publishes; it never calls the shop and exposes no
customer-facing query API.

| Direction | Mechanism | Owner |
|---|---|---|
| `offer management` → `browsing offer` | domain events | this context |

Evidence: transcript seg. 54–56 — the catalog supplies `search / product details / prices`;
stock comes from the warehousing system. The catalog's job is to **say what exists**; the
shop decides how to serve it.

The `GET /products…` surface in element 02 is the **admin** surface of this context
(Q22), not something the shop calls.

## The published contract

The event records are defined in element 04 §11; this element is the consumer-facing view of
them.

| Event | When | Consumer effect |
|---|---|---|
| `BlankDraftCreated` | product created | **not published** — private to this context (transcript seg. 9–10: *changes are private until published*) |
| `DescriptionUpdated` | draft edited | **not published** — private |
| `PhotoAddedInRightFormats` / `PhotoRemoved` | photo attached or removed | **not published** — private |
| `DescriptionPendingReview` / `DescriptionReviewApproved` / `DescriptionReviewRejected` | review transitions | **not published** — private |
| `ProductVersionPublishedToOffer` | a version is published, with `availableFrom` | the shop adds/updates the product offer, honoring the availability date |
| `ProductPricesChanged` | a price or discount becomes `ACTIVE` | the shop updates the price (and the effective price) it displays |
| `ProductRemovedFromOffer` | product removed from the offer | the shop marks the product unavailable; it must **not** disappear from existing baskets (transcript seg. 69) |
| `DescriptionReverted` | revert produced a new draft | **not published** — private until published |

**F1 — eight of eleven events never cross the boundary.** Only three do:
`ProductVersionPublishedToOffer`, `ProductPricesChanged`, `ProductRemovedFromOffer`. This is
the rule the transcript states at seg. 9–10 (seg. 34 states the publication fact, not the privacy rule).

## Payloads the shop receives

```yaml
ProductVersionPublishedToOffer:
  productId: p-2019-0442
  version: v3
  availableFrom: "2019-07-01"        # null = visible immediately
  title: "Prosto z półki / Kosiarka ręczna 340"
  description: "Solidna kosiarka ręczna do trawy i chwastów…"
  attributes: { category: Ogród, manualUrl: null }
  photos: [ { photoId: ph-1, mime: image/jpeg, width: 1200, height: 1200 } ]
  audit: { who: m.nowak, at: "2019-06-28T08:00:00Z" }

ProductPricesChanged:
  productId: p-2019-0442
  effectiveFrom: "2019-07-01"
  price: { value: "259.00", currency: PLN }
  discountPercent: "10"              # null when no discount is active
  audit: { who: s.zielinski, at: "2019-06-20T11:00:00Z" }

ProductRemovedFromOffer:
  productId: p-2019-0442
  audit: { who: a.kowalska, at: "2019-10-07T09:00:00Z" }
```

A full description travels with `ProductVersionPublishedToOffer` because the shop cannot
reconstruct it — the draft and its history are private (F1).

## Domain events vs integration events

Two deliberately separate layers:

| Layer | Defined in | Carries |
|---|---|---|
| **Domain event** | element 04 §11 | the aggregate id, its own change and the audit — never derived state (RULE-63) |
| **Integration event** | this element | the same fact plus the resolved view the shop needs to build its own read models |

| Domain event | Integration event | Extra fields in the integration layer |
|---|---|---|
| `ProductVersionPublishedToOffer` | `ProductVersionPublishedToOffer` | `title`, `description`, `attributes`, `photos` — the draft is private, so the shop cannot fetch them |
| `ProductPricesChanged` | `ProductPricesChanged` | `effectiveFrom`, `price`, `discountPercent` — the resolved view, so the shop does not re-implement element 06 |
| `ProductRemovedFromOffer` | `ProductRemovedFromOffer` | — |

`effectiveFrom` is the integration-layer name for the domain event's `changedAt` — the business
date the change takes effect (RULE-28).

## Delivery semantics

| Property | Decision |
|---|---|
| Delivery | at-least-once |
| Idempotency key | `(productId, version)` for a published version; `(productId, effectiveFrom)` for prices; `(productId, removedAt)` for removal |
| Ordering | per `productId` partition — events for one product arrive in order |
| Out-of-order price events | resolved by the shop on `effectiveFrom`; a late event for an earlier date is ignored |
| Replay | supported — the shop must be able to rebuild from a full stream |
| Failure | a consumer failure never blocks publishing; the shop catches up from the stream |
| Schema evolution | `EventTypes` type names are versioned `_v1`; additive changes only, no field removal without `_v2` |

## What the shop must build from this (out of scope here)

`search`, `product details`, `current prices`, plus stock from the warehouse port — the
`browsing offer` spec owns all of it (ticket [#3](https://github.com/michal-michaluk/ecommerce-ai/issues/3)).

## Open

- **Q17** — confirm: events only, no query API for the shop. This element assumes the proposed answer.
- **Q38** — transport: a broker (Kafka/AMQP) or an outbox polled by the shop? The blueprint has no messaging adapter doc, so this decides whether a new adapter type is introduced.
- **Q39** — does the shop need `DescriptionReverted` to invalidate anything it already projected? Currently no, because revert only creates a draft — confirm.
- **R4** — the illegible board annotation (`Event(?): Product(published) calloff offer(...) productId}`) may describe exactly this boundary. It is not used; if it names a mechanism, this element changes.
