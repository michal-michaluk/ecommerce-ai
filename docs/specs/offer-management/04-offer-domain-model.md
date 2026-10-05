# Element 04 — Offer domain model

Concepts, roles, examples, rules and the lifecycle of an offer. Names here are the
ubiquitous language for every other element.

## 1. Concept roles

| Concept | Role | Mutability | Identity |
|---|---|---|---|
| **Product** | Process | stateful, mutable | `productId` |
| **DescriptionDraft** | Editor (draft flavour) | mutable, disposable | per product — one live draft |
| **DescriptionVersion** | Definition | immutable, versioned | `productId` + `version` |
| **Photo** | Definition | immutable once attached | `photoId` |
| **ReviewRequest** | Execution | append-only + projection | `reviewRequestId` |
| **Price** | Definition | immutable once active | `priceId` |
| **Discount** | Definition | immutable once active | `discountId` |
| **Publication** | Execution | append-only + projection | `publicationId` |
| **OfferState** | Derived | immutable output | recomputed |
| **Completeness** | Derived | immutable output | recomputed — see [05](05-review-completeness.md) |
| **EffectivePrice** | Derived | immutable output | recomputed — see [06](06-effective-price.md) |

*design → freeze → execute*: the **DescriptionDraft** never executes, the
**DescriptionVersion** never mutates, the **Publication** never edits a version — it pins one.

**Shared kernel:** `Identity(subject)` and `Audit(who, at)` live in `tools/` (blueprint
`code-structure.md`) and are attached to every state-modifying operation. The Java shape is in
[`prototypes/domain-model-java.md`](prototypes/domain-model-java.md).

**Decision:** "offer" is **not** a concept of this context. The offer belongs to the shop;
this context owns `Product.offerPresence` (`PRESENT` / `REMOVED`) plus the events it
publishes (element 03). `OfferState` is the derived, display-facing summary of that.

## 2. Domain story

`Actor → action → Concept (role): outcome`

1. Content Manager → creates → **Product** (Process): identity, `offerPresence = PRESENT`
2. Content Manager → fills → **DescriptionDraft** (Editor): title, description, attributes
3. Photographer → attaches → **Photo** (Definition): immutable image in an allowed format
4. DescriptionDraft → derives → **Completeness** (Derived): the list of missing items
5. Content Manager → requests → **ReviewRequest** (Execution): `description pending review`
6. Reviewer → decides → **ReviewRequest** (Execution): approved or rejected
7. Product → freezes → **DescriptionVersion** (Definition): new immutable version
8. Content Manager → publishes → **Publication** (Execution): pins one version + `availableFrom`
9. Sales → defines → **Price** / **Discount** (Definition): dated ranges, scheduled
10. **Publication** + **Price** + **Discount** → derive → **OfferState** / **EffectivePrice**
11. Content Manager → reverts → Product (Process) → a new **DescriptionDraft** based on an older version (never a new version — RULE-12)
12. Content Manager → removes → **Product** (Process): `offerPresence = REMOVED`

## 3. Product (Process)

Coordinates the offer lifecycle across the other concepts and owns every transition.

```yaml
productId: p-2019-0442
category: Ogród
offerPresence: PRESENT
visibleVersion: v2          # what the customer sees now, or null
scheduledVersion: v3        # a version with a future availableFrom, or null
draftState: EDITING         # NONE | EDITING | IN_REVIEW | APPROVED
createdAt: "2019-03-11T10:00:00Z"
state: PUBLISHED            # derived, see OfferState
```

```yaml
productId: p-2019-0510
category: Ogród
offerPresence: PRESENT
visibleVersion: null
scheduledVersion: null
draftState: APPROVED        # approved, but no price is defined
createdAt: "2019-10-04T16:40:00Z"
state: BLOCKED              # derived, see OfferState
```

**Behaviour** — create product (starts a draft), request review, approve/reject, publish,
revert, remove from offer.

**Domain rules**

- RULE-1: a Product has **exactly one** live DescriptionDraft; it exists while `draftState ≠ NONE`.
- RULE-2: `offerPresence` is `PRESENT` from creation; removal sets `REMOVED` and is idempotent.
- RULE-3: removal never deletes a DescriptionVersion or a Publication — `visibleVersion` stops being exposed.
- RULE-4: `visibleVersion` and `scheduledVersion` are projections of Publications, never edited directly.

## 4. DescriptionDraft (Editor)

The disposable working surface. Never authoritative, never visible to the customer.

```yaml
productId: p-2019-0442
draftState: EDITING
title: "Prosto z półki / Kosiarka ręczna 340"
description: "Solidna kosiarka ręczna do trawy i chwastów. Szerokość robocza 34 cm."
attributes: { category: "Ogród", manualUrl: null }
photoIds: [ph-1, ph-2]
basedOnVersion: null
reviewRequestId: null
lastSavedAt: "2019-10-02T09:10:00Z"
```

```yaml
productId: p-2019-0510
draftState: IN_REVIEW
title: "Stół piknikowy"
description: "Składany stół z drewna sosnowego, blat 120 × 70 cm."
attributes: { category: "Ogród", manualUrl: null }
photoIds: []
basedOnVersion: null
reviewRequestId: rr-2
lastSavedAt: "2019-10-04T16:38:00Z"
```

```yaml
productId: p-2019-0442
draftState: NONE
```

The third instance is the state after publishing: the draft is consumed and gone. `NONE` is
**not** a value of the aggregate's state enum — it means *no draft exists*. The Java enum
therefore carries only `EDITING`, `IN_REVIEW`, `APPROVED` (element 04 §4, RULE-1).

**Domain rules**

- RULE-5: a draft is editable **only** while `draftState = EDITING`; `IN_REVIEW` rejects writes.
- RULE-6: `title` is mandatory from creation; every other field is optional.
- RULE-7: `basedOnVersion` is set only by revert, and points at an existing version.
- RULE-8: a draft is disposable — discarding it never affects a published version.

## 5. DescriptionVersion (Definition)

Immutable, versioned snapshot created when an approved draft is published.

```yaml
productId: p-2019-0442
version: v2
title: "Kosiarka ręczna 340"
description: "Solidna kosiarka ręczna do trawy i chwastów. Szerokość robocza 34 cm."
attributes: { category: "Ogród", manualUrl: null }
photoIds: [ph-1, ph-2]
basedOnVersion: null
approvedBy: "m.nowak"
createdAt: "2019-05-01T09:00:00Z"
```

```yaml
productId: p-2019-0442
version: v3
title: "Prosto z półki / Kosiarka ręczna 340"
description: "Solidna kosiarka ręczna do trawy i chwastów. Szerokość robocza 34 cm, wysokość cięcia regulowana w 4 stopniach."
attributes: { category: "Ogród", manualUrl: null }
photoIds: [ph-1, ph-2]
basedOnVersion: v2
approvedBy: "m.nowak"
createdAt: "2019-06-20T08:00:00Z"
```

The two instances differ in title, description and lineage — `v3` is the reverted-and-edited
successor of `v2`, and the photo set is shared, not copied.

A version has no stored state; it is derived from its Publication:

```text
VersionState(version, at) =
  SCHEDULED   if its Publication exists and availableFrom > at
  PUBLISHED   if its Publication is visible at `at`
  SUPERSEDED  otherwise (a later version is visible)
```

`DRAFT` and `APPROVED` are **draft** states, never version states — a version comes into
existence only when an approved draft is published (RULE-10).

**Domain rules**

- RULE-9: a DescriptionVersion is **immutable**; any change produces a new version.
- RULE-10: a version is created only from a draft whose ReviewRequest is `APPROVED`.
- RULE-11: versions are numbered `v1, v2, …`, strictly increasing per product, never reused.
- RULE-12: **revert** produces a *new draft* with `basedOnVersion` set — it copies an older version into a working draft and the normal review/publish path follows. History is never rewritten (P4).
- RULE-13: a version references Photo identities; it never owns image bytes.

## 6. Photo (Definition)

```yaml
photoId: ph-1
fileName: kosiarka-01.jpg
mime: image/jpeg
width: 1200
height: 1200
sizeBytes: 184320
position: 0
uploadedAt: "2019-10-01T12:20:00Z"
```

```yaml
photoId: ph-3
fileName: kosiarka-03.png
mime: image/png
width: 1000
height: 1000
sizeBytes: 512000
position: 2
uploadedAt: "2019-10-05T10:01:00Z"
```

**Domain rules**

- RULE-14: a Photo is immutable once attached; replacing means attach new + detach old.
- RULE-15: `position` is unique within a draft and contiguous from 0.
- RULE-16: `mime`, `width` and `height` must satisfy the format policy — **[Q21]** (currently invented).

## 7. ReviewRequest (Execution)

```yaml
reviewRequestId: rr-1
productId: p-2019-0442
draftRevision: 7              # draft revision under review
author: "a.kowalska"
decidedBy: null               # pending
at: "2019-10-02T09:14:00Z"    # submitted at
```

```yaml
reviewRequestId: rr-1
productId: p-2019-0442
draftRevision: 7
author: "a.kowalska"
decidedBy: "m.nowak"
at: "2019-10-05T10:20:00Z"    # decided at
reason: "Opis niekompletny."
```

**Domain rules**

- RULE-17: at most one ReviewRequest is `PENDING` per product (RULE-6 makes the draft read-only meanwhile).
- RULE-18: the reviewer is **never** the author (P5). Enforced on the decision, not on the queue read.
- RULE-19: a decision is terminal — a ReviewRequest is never reopened.
- RULE-20: approval does **not** publish; it only unlocks publication (P2).
- RULE-21: rejecting returns the draft to `EDITING`; a new request is a new ReviewRequest.

## 8. Price and Discount (Definition)

Two concepts, one role: a dated value that makes up the price of an offer.

```yaml
priceId: pr-2
productId: p-2019-0442
kind: PRICE
amount: { value: "259.00", currency: "PLN" }
validFrom: "2019-07-01"
validTo: null
state: SCHEDULED              # SCHEDULED | ACTIVE | EXPIRED
```

```yaml
priceId: pr-3
productId: p-2019-0442
kind: DISCOUNT
percent: "10"
validFrom: "2019-07-01"
validTo: "2019-07-31"
state: SCHEDULED
```

Second pair, differing in kind and in openness (`pr-2` is open-ended, `pr-3` is bounded):

```yaml
priceId: pr-1
productId: p-2019-0442
kind: PRICE
amount: { value: "249.00", currency: "PLN" }
validFrom: "2019-06-01"
validTo: "2019-06-30"
state: EXPIRED
```

```yaml
priceId: pr-4
productId: p-2019-0442
kind: DISCOUNT
percent: "15"
validFrom: "2019-08-01"
validTo: "2019-08-15"
state: SCHEDULED
```

**Domain rules**

- RULE-22: exactly one of `amount` / `percent` is set — `amount` for `PRICE`, `percent` for `DISCOUNT`.
- RULE-23: `validFrom` is mandatory; `validTo` is optional and means open-ended when null.
- RULE-24: when both are set, `validTo` ≥ `validFrom`.
- RULE-25: entries of the same kind never overlap for one product — **[Q18]** (rounding/units still open).
- RULE-26: a `SCHEDULED` entry is editable and deletable; `ACTIVE` and `EXPIRED` are immutable.
- RULE-27: an entry is `ACTIVE` only when `validFrom ≤ now < validTo` (open-ended: `now ≥ validFrom`); otherwise `SCHEDULED` or `EXPIRED`.
- RULE-28: changes take effect on the date, not at the moment of entry (P3) — `product prices changed` is emitted when an entry becomes `ACTIVE`.

## 9. Publication (Execution)

Pins a version and decides when it becomes visible.

```yaml
publicationId: pub-2
productId: p-2019-0442
version: v3
availableFrom: "2019-07-01"
createdAt: "2019-06-28T08:00:00Z"
exposedAt: null               # set when it becomes visible
```

```yaml
publicationId: pub-1
productId: p-2019-0442
version: v2
availableFrom: "2019-05-02"
createdAt: "2019-05-01T09:00:00Z"
exposedAt: "2019-05-02T00:00:00Z"
```

**Domain rules**

- RULE-29: a Publication pins exactly one DescriptionVersion; it never edits it.
- RULE-30: a Publication is created only for an `APPROVED` version, and only when Completeness reports nothing missing.
- RULE-31: `availableFrom` in the future means the version is not visible yet; the previously visible version stays visible (P3).
- RULE-32: at most one version is visible at any instant; a later `availableFrom` supersedes an earlier one at its own date.
- RULE-33: publications are append-only; cancelling a scheduled publication is a new state, not a delete.

## 9a. VisibleVersion (Derived)

Which version the customer sees at a given instant — the projection the shop read model
consumes (element 03).

```text
VisibleVersion(product, at) =
    none                                    if offerPresence = REMOVED
    else argmax(P.availableFrom) over Publications P where P.availableFrom ≤ at
```

The same product at two instants:

```diff
 productId: p-2019-0442
-at: 2019-06-15
-visibleVersion: v2
+at: 2019-07-01
+visibleVersion: v3
```

```diff
 productId: p-2019-0442
-at: 2019-07-01
-visibleVersion: v3
+at: 2019-07-01 (offerPresence REMOVED)
+visibleVersion: null
```

- RULE-40: `VisibleVersion` ignores all Publications when `offerPresence = REMOVED`.

---

## 10. OfferState (Derived)

The display state the UI shows in the catalog (element 01) and the `state` field in the API
(element 02). Recomputed, never stored as truth.

```text
OfferState(product) =
  REMOVED          if offerPresence = REMOVED
  PUBLISHED        else if visibleVersion ≠ null
  SCHEDULED        else if scheduledVersion ≠ null
  PENDING_REVIEW   else if draftState = IN_REVIEW
  BLOCKED          else if draftState = APPROVED and Completeness(product).missing ≠ ∅
  DRAFT            otherwise
```

`BLOCKED` requires an **approved** version that the publish guard refuses — not merely a draft
with missing items. A fresh draft is always `DRAFT`; otherwise every new product would be born
`BLOCKED`.

The same product in two conditions:

```diff
 productId: p-2019-0442
-state: PUBLISHED
+state: SCHEDULED
 visibleVersion: v2
+scheduledVersion: v3
```

```diff
 productId: p-2019-0510     # draft APPROVED, but no price is defined
-state: DRAFT
+state: BLOCKED
```

**Domain rules**

- RULE-34: `OfferState` is derived — no command sets it directly.
- RULE-35: the precedence above is total; exactly one value always applies.
- RULE-70: `BLOCKED` means an approved version exists and the publish guard (D2) refuses it; a draft that is merely incomplete is `DRAFT`.

## 11. Domain events

Canonical names are the board's; `(+)` marks a name this context adds because the board
does not name it. Each record carries **its own change plus an `Audit`** — never the whole
aggregate, never derived state (RULE-63). The Java records are in
[`prototypes/domain-model-java.md`](prototypes/domain-model-java.md).

| Board name | Event record | Carries |
|---|---|---|
| `blank draft created` | `BlankDraftCreated` | the full opening state + audit |
| `description updated` | `DescriptionUpdated` | draft revision + the new title/description/attributes + audit |
| `photo added in right formats` | `PhotoAddedInRightFormats` | the new `Photo` (position assigned) + audit |
| `photo removed` `(+)` | `PhotoRemoved` | `photoId` + audit — the resulting order is deterministic (RULE-15) |
| `description pending review` | `DescriptionPendingReview` | the `ReviewRequest` + audit |
| `description review approved` `(+)` | `DescriptionReviewApproved` | the decided `ReviewRequest` + audit |
| `description review rejected` `(+)` | `DescriptionReviewRejected` | the decided `ReviewRequest` + reason + audit |

```yaml
- BlankDraftCreated:          { productId: p-2019-0442, version: v1, revision: 1, state: EDITING,
                               title: "Nowy produkt", description: null, attributes: { category: Ogród,
                               manualUrl: null }, photos: [], basedOnVersion: null, review: null,
                               audit: { who: "a.kowalska", at: "2019-10-01T09:00:00Z" } }
- DescriptionUpdated:         { productId: p-2019-0442, revision: 7,
                               title: "Prosto z półki / Kosiarka ręczna 340",
                               description: "Solidna kosiarka ręczna…",
                               attributes: { category: Ogród, manualUrl: null },
                               audit: { who: "a.kowalska", at: "2019-10-02T09:10:00Z" } }
- PhotoAddedInRightFormats:   { productId: p-2019-0442, photo: { photoId: ph-3, position: 2 },
                               audit: { who: "p.wrona", at: "2019-10-01T12:20:00Z" } }
- PhotoRemoved:               { productId: p-2019-0442, photoId: ph-3,
                               audit: { who: "a.kowalska", at: "2019-10-01T13:00:00Z" } }
- DescriptionPendingReview:   { productId: p-2019-0442,
                               review: { reviewRequestId: rr-1, author: "a.kowalska",
                               decidedBy: null, at: "2019-10-02T09:14:00Z" },
                               audit: { who: "a.kowalska", at: "2019-10-02T09:14:00Z" } }
- DescriptionReviewApproved:  { productId: p-2019-0442,
                               review: { reviewRequestId: rr-1, author: "a.kowalska",
                               decidedBy: "m.nowak", at: "2019-10-05T10:20:00Z" },
                               audit: { who: "m.nowak", at: "2019-10-05T10:20:00Z" } }
- DescriptionReviewRejected:  { productId: p-2019-0442,
                               review: { reviewRequestId: rr-1, author: "a.kowalska",
                               decidedBy: "m.nowak", at: "2019-10-05T10:20:00Z" },
                               reason: "Opis niekompletny.",
                               audit: { who: "m.nowak", at: "2019-10-05T10:20:00Z" } }
- ProductVersionPublishedToOffer:
                             { productId: p-2019-0442, version: v3, availableFrom: "2019-07-01",
                               audit: { who: "m.nowak", at: "2019-06-28T08:00:00Z" } }
- ProductPricesChanged:       { productId: p-2019-0442, changedAt: "2019-07-01",
                               audit: { who: "s.zielinski", at: "2019-06-20T11:00:00Z" } }
- DescriptionReverted:        { productId: p-2019-0442, newVersion: v4, basedOnVersion: v2,
                               audit: { who: "a.kowalska", at: "2019-10-06T09:00:00Z" } }
- ProductRemovedFromOffer:    { productId: p-2019-0442,
                               audit: { who: "a.kowalska", at: "2019-10-07T09:00:00Z" } }
```

## 11a. Audit and actor identity

Every state-modifying operation takes an `Audit(who, at)`; the aggregate stores the last one as
`lastChange` and the corresponding event carries it.

- RULE-60: the acting `Audit.who` must equal the `ReviewRequest` author (on request) or the decider (on decision) — otherwise `ActorMismatch`. A caller cannot forge the review actor.
- RULE-61: every state-modifying operation sets `lastChange = audit`; an idempotent no-op changes nothing and emits nothing.
- RULE-62: the actor is resolved once, in the adapter, from the JWT subject; the domain never sees a token, a request or a principal.
- RULE-63: an event carries the aggregate id, its own change and the audit — never the whole aggregate, never derived state.
- RULE-68: `Price` and `Discount` are an aggregate of their own (`PriceSchedule`), independent of `DescriptionDraft` — the two meet only as a price-presence flag in `Completeness` and are resolved together at publication and read time.
- RULE-69: no aggregate holds a `Clock`; the business date is resolved by the service and passed in, and `Audit(who, at)` carries the acting identity and instant.

## 12. Open

- **Q20** — state names (`DRAFT · PENDING_REVIEW · PUBLISHED · SCHEDULED · BLOCKED · REMOVED`),
  the mandatory-item set behind Completeness and the advisory grammar check are **invented**;
  RULE-16, RULE-30 and OfferState depend on them.
- **Q18** — currency, units and rounding; RULE-25 (overlap) is not fully defined.
- **Q21** — the photo format policy behind RULE-16.
- **Q25 — fixed.** The element-01 catalog rows showed a published version under state `DRAFT`
  / `BLOCKED`. The rows were corrected to satisfy the precedence (six rows, one per state),
  `BLOCKED` was tightened by RULE-70 so a merely incomplete draft stays `DRAFT`, the gate
  re-ran green and the screenshots were re-shot.
- **Q26** — should a `SCHEDULED` Publication be cancellable by the content manager
  (RULE-33 says a new state, not a delete) — and is that in scope for this increment?
