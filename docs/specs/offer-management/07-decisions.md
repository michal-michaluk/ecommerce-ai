# Element 07 — Offer management decisions

Five automated decisions. Each is a decision table with named rules; the executable
scenario rows are in the tables themselves.

Every decision returns either **allow** (the command proceeds) or **deny** with the error
code the frontend API declares (element 02).

---

## D1 — description quality policy

Evaluated on demand over a draft; its result is what the reviewer sees at the gate.

| Rule | `missing[]` | advisory issues | → outcome | gate effect |
|---|---|---|---|---|
| Q1 | `[]` | none | `PASS` | publication allowed |
| Q2 | `[]` | ≥ 1 | `PASS_WITH_ISSUES` | publication still allowed — advisory never blocks |
| Q3 | ≥ 1 | any | `FAIL` | publication denied, `blocking = missing[]` |

Inputs: `Completeness(draft)` (element 05), advisory issues from the text-check port.

- RULE-48: only `missing[]` can block; `issues[]` is reported, never blocking.
- RULE-49: the policy never blocks `request review` — only publication (see D3).

---

## D2 — publish guard

Determines whether a Publication may be created for a version (element 04 §9, RULE-30).

| Rule | version exists | version approved | `Completeness.complete` | → outcome |
|---|---|---|---|---|
| P1 | yes | yes | yes | allow → `Publication` created |
| P2 | yes | yes | no | deny `422 PUBLICATION_BLOCKED`, `details.blocking[] = missing[]` |
| P3 | yes | no | — | deny `409 VERSION_NOT_APPROVED` |
| P4 | no | — | — | deny `404 VERSION_NOT_FOUND` |

Rule order matters: P4 before P2 before P3 is **not** how it evaluates — the most specific
failure wins: not-found → not-approved → blocked.

- RULE-50: the guard reads the frozen version plus the current price state; it never re-derives the draft.
- RULE-51: an approval is a precondition of publication, never a substitute for the guard (RULE-20).

---

## D3 — review request guard

Determines whether a draft may enter review. This is the deliberate asymmetry of the
quality gate (P2): requesting a review **with** missing items is allowed, publishing is not.

| Rule | `draftState` | `missing[]` | → outcome |
|---|---|---|---|
| V1 | `EDITING` | `[]` | allow → `ReviewRequest` `PENDING` |
| V2 | `EDITING` | ≥ 1 | **allow** — the reviewer decides; `missingCount` travels with the request |
| V3 | `IN_REVIEW` | — | deny `409 REVIEW_ALREADY_PENDING` |
| V4 | `NONE` (no draft) | — | deny `409 DRAFT_NOT_FOUND` |

- RULE-52: the request guard never consults the quality policy.
- RULE-53: at most one `PENDING` ReviewRequest exists per product (RULE-17).

---

## D4 — publication timing

Determines the state of a newly created Publication and when it becomes visible (P3).

| Rule | `availableFrom` | → state | → visible from |
|---|---|---|---|
| T1 | `null` | `PUBLISHED` | now |
| T2 | today or in the past | `PUBLISHED` | `availableFrom` |
| T3 | in the future | `SCHEDULED` | `availableFrom` |
| T4 | before the product's creation date | deny `422 VALIDATION_FAILED` | — |

`VisibleVersion` (element 04 §9a) is the projection this feeds.

- RULE-54: an `availableFrom` in the past never back-dates visibility; the version becomes visible immediately.
- RULE-55: scheduling a version does not hide the currently visible one (RULE-31).

---

## D5 — separation of duties

| Rule | deciding actor vs draft author | → outcome |
|---|---|---|
| S0 | the review is not `PENDING` | deny `409 REVIEW_NOT_PENDING` |
| S1 | different person | allow approve / reject |
| S2 | same person | deny `403 REVIEWER_IS_AUTHOR` |

- RULE-56: evaluated on the **decision**, not on reading the review queue — a person may see their own pending review.
- RULE-57: identity is the authenticated subject of the token; there is no delegation in this increment (**Q29**).

---

## Cross-decision rules

- RULE-58: every deny carries the exact error code from element 02's error-code table.
- RULE-59: decisions are deterministic — same inputs, same outcome; no clock is read except in D4/T2 and `VisibleVersion`.

## Open

- **Q29** — may a reviewer delegate or be replaced if they are the only content manager available?
- **Q20** — D1/Q2 and the requirement catalogue behind D2/P2 are **proposed**, not confirmed.
- **Q27** — without the text-check port, D1/Q2 is unreachable.
