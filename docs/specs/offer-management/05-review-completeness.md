# Element 05 — review completeness

Derived concept. Turns a draft plus the requirement catalogue into the list of things that
are missing. It is what the draft screen (P1) shows beside the fields, what the reviewer
sees at the gate, and what blocks publication (D2/P3).

**Role:** Derived — immutable output, recomputed, never set by a command.

## Formula

```text
Completeness(draft) =
    [ TITLE_REQUIRED       if blank(draft.title) ]
  ∪ [ DESCRIPTION_REQUIRED if blank(draft.description) ]
  ∪ [ PHOTO_REQUIRED       if count(draft.photos) = 0 ]
  ∪ [ PRICE_REQUIRED       if no Price of this product is ACTIVE or SCHEDULED ]

complete = ( missing = ∅ )
ordered by the requirement catalogue order: TITLE, DESCRIPTION, PHOTO, PRICE
```

`blank(x)` = absent, or empty/whitespace-only after trimming.

## Requirement catalogue

The catalogue is **data**, not code (RULE-36): adding or removing a mandatory item changes
configuration, not the calculation.

| code | label | predicate | blocks publication |
|---|---|---|---|
| `TITLE_REQUIRED` | Title | `title` non-blank | yes |
| `DESCRIPTION_REQUIRED` | Description | `description` non-blank | yes |
| `PHOTO_REQUIRED` | At least one photo | `count(photos) ≥ 1` | yes |
| `PRICE_REQUIRED` | Price valid for a date range | ∃ Price with state ≠ `EXPIRED` | yes |

## Scenario table

| # | title | description | photos | price state | → `complete` | → `missing[]` |
|---|---|---|---|---|---|---|
| 1 | set | set | 2 | ACTIVE | true | `[]` |
| 2 | set | set | 2 | SCHEDULED | true | `[]` |
| 3 | set | set | 2 | EXPIRED | false | `[PRICE_REQUIRED]` |
| 4 | set | set | 2 | none | false | `[PRICE_REQUIRED]` |
| 5 | set | set | 0 | ACTIVE | false | `[PHOTO_REQUIRED]` |
| 6 | set | `""` | 2 | ACTIVE | false | `[DESCRIPTION_REQUIRED]` |
| 7 | `""` | `""` | 0 | none | false | `[TITLE_REQUIRED, DESCRIPTION_REQUIRED, PHOTO_REQUIRED, PRICE_REQUIRED]` |
| 8 | set | set | 1 | ACTIVE | true | `[]` — exactly one photo is enough |

## Advisory issues (not part of `missing`)

An optional text check reports `issues[]` with `severity: ADVISORY`. Advisory issues **never**
appear in `missing[]` and never change `complete` — they are reported next to it (D1/Q2).
The checker is a port with a null-object default: with no checker configured, `issues = []`.

## Parameter and value-object origins

| Parameter | Origin |
|---|---|
| `draft.title`, `draft.description`, `draft.photos` | DescriptionDraft (Editor) — element 04 §4 |
| price presence | Price (Definition) — element 04 §8 |
| requirement catalogue | configuration |
| advisory issues | text-check port — **Q27** |

## Domain rules

- RULE-36: `Completeness` is derived from the requirement catalogue; the catalogue is configuration, not code.
- RULE-41: `Completeness` is evaluated against the **draft**, never against a published version.
- RULE-42: a requirement that is met contributes nothing — `missing[]` carries only unmet items.
- RULE-43: `missing[]` order is the catalogue order, never insertion order.
- RULE-44: advisory issues are outside `complete` and outside `missing[]`.

## Open

- **Q27** — is the advisory text check in this increment?
- **Q28** — stored projection `ProductCompleteness` (proposed); this file defines the calculation, the projection only caches its output.
