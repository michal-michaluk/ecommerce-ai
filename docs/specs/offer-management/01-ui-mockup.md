# Element 01 — UI mockup

Low-fidelity, click-through prototype of the `offer management` admin screens.

**Artifact:** [`prototypes/README.md`](prototypes/README.md) ·
storybook gallery in `.storybook/` (case **C** of the skill's `ui-prototype.md` — isolated,
no frontend scaffolding) · screenshots in [`prototypes/ui-mockups/`](prototypes/ui-mockups/).

## Stories

| Story id | Screen |
|---|---|
| `offer-management--catalog` | 01 Product catalog — product list with states |
| `offer-management--create` | 02 Create product — blank draft |
| `offer-management--draft` | 03 Draft workspace — title/description + *what is missing* |
| `offer-management--photos` | 04 Photos — product photos, allowed formats |
| `offer-management--queue` | 05 Review queue — pending reviews |
| `offer-management--review` | 06 Review detail — quality gate, publication blocked |
| `offer-management--publish` | 07 Publish — availability date, scheduled publication |
| `offer-management--pricing` | 08 Pricing — prices and discounts for date ranges |
| `offer-management--versions` | 09 Versions and removal — history, revert, remove from offer |

## Click-through flows (all verified)

- happy path — catalog → create → draft → review → publish → catalog
- photo/pricing branches — draft → photos → review; draft → pricing → catalog
- rejection — review → draft
- revert — review → versions → draft
- removal — versions → catalog

## Run and gate

```bash
cd .storybook && npm install && npm run storybook     # http://localhost:6006
NODE_PATH="$(pwd)/node_modules" node ./verify-storybook.cjs http://localhost:6006
```

Last run: **9/9 stories passed, 4/4 flows passed** — no console errors, no HTTP errors.

## Properties the mockup demonstrates

| Property | Where it is visible |
|---|---|
| P1 continuous feedback | story 03 — *What is missing* panel beside the fields |
| P2 publish quality gate | story 06 — "Publication is blocked"; story 07 — gate passed, publish enabled |
| P3 scheduled effect | story 07 — availability date; story 08 — scheduled price rows |
| P4 version + snapshot | story 09 — version history with revert |
| P5 separation of duties | story 06 — review is not performed by the author |
| P7 photos | story 04 — photos from any source, format hints |

## Consistency with element 04

The catalog shows **one product per `OfferState`** — `PUBLISHED`, `PENDING_REVIEW`,
`SCHEDULED`, `BLOCKED`, `DRAFT`, `REMOVED` — so every branch of the derivation (element 04 §10)
is visible. Catalog badges show the product's `OfferState`; the draft screens show the draft's
`DraftState` (`EDITING`, `IN_REVIEW`, `APPROVED`), which is a different enum.

## Open

- **Q20** — the mockup contains invented specifics (mandatory-item set, advisory grammar
  check, PLN, `−10 %/−15 %` discounts, state names, "approve allowed with missing items,
  publish not"). Except for the four requirements (transcript seg. 5, 17, 27, 41), these are
  **not** in the materials and must be confirmed before they harden into elements 04–08.
- **Q21** — the photo format policy shown in story 04 (`JPG / PNG`, min 1000×1000) is invented.
- **Q24** — the mockup is Polish-labelled, with sample product names in the style of
  `raw/offer-lego.png` (Allegro). Confirm the language of the admin UI and that nothing from
  the Allegro layout is normative.
