# Element 01 — UI mockup (low-fi)

Low-fidelity, click-through prototype of the `offer management` screens. Case **C** of the
`specification` skill's `ui-prototype.md` (no frontend exists; frontend scaffolding
skipped by operator decision) — Storybook is isolated under `.storybook/` and does not
touch the future application code.

## Story names

| Story (id) | Screen |
|---|---|
| `offer-management--catalog` | 01 Product catalog — product list with statuses |
| `offer-management--create` | 02 Create product — blank draft |
| `offer-management--draft` | 03 Draft workspace — edit title/description + what is missing |
| `offer-management--photos` | 04 Photos — product photos, allowed formats |
| `offer-management--queue` | 05 Review queue — pending reviews |
| `offer-management--review` | 06 Review detail — quality gate, blocked publication |
| `offer-management--publish` | 07 Publish — availability date, scheduled publication |
| `offer-management--pricing` | 08 Pricing — prices and discounts for date ranges (Sales) |
| `offer-management--versions` | 09 Versions and removal — version history, revert, remove from offer |

Screenshots of every story: [`ui-mockups/`](ui-mockups/).

## Click-through flow

Every screen's top navigation and every action button navigates to the next story, so the
whole offer lifecycle can be clicked end to end:

- happy path — catalog → create → draft → review → publish → catalog
- photo / pricing branches — draft → photos → review; draft → pricing → catalog
- rejection — review → draft
- revert — review → versions → draft
- removal — versions → catalog

## Run it

```bash
cd .storybook
npm install
npm run storybook          # http://localhost:6006
```

## Quality gate

The skill's `references/verify-storybook.cdp.js` cannot drive this Storybook — it
enumerates `#storybook-explorer-tree a` (Storybook 10 renders buttons, not anchors) and
checks `#storybook-root` on the manager page, while stories render inside
`#storybook-preview-iframe`. `.storybook/verify-storybook.cjs` is the equivalent gate over
the real preview surface (`iframe.html?id=<storyId>`):

```bash
cd .storybook
NODE_PATH="$(pwd)/node_modules" node ./verify-storybook.cjs http://localhost:6006
```

Last run: **9/9 stories passed, 4/4 flows passed, no console errors, no HTTP errors.**

## Not designed here

- Real visual design system, theming, responsiveness, accessibility — this is intentionally
  low-fi.
- Any state beyond those needed to make the lifecycle click-through understandable.
