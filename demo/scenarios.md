# Acceptance scenarios — offer-management implementation increment

Source: `docs/specs/offer-management/` (§11) and `docs/prove-of-done.md` (I1–I7).
Surface under demo: the live k3s stack (`http://localhost:8080`, Keycloak `http://localhost:18081`),
the `browsing offer` consumer read model (`http://localhost:18090`) and the Storybook admin prototype
(`http://localhost:6006`).

One chapter per scenario in `demo/offer-management.chapters.mkv`.

## Scenario ↔ DoD-row mapping

The seven scenarios below follow the plan brief's numbering, which is **not** the I1–I7 numbering of
`docs/specs/offer-management/spec.md` / `docs/prove-of-done.md`. The recording's closing summary prints
this mapping so the two documents can be read together:

| scenario | chapter | DoD row |
|---|---|---|
| 1 Artifact | 1 | I1 |
| 2 Contract | 2 | I2 (and I3 — the declared error pairs) |
| 3 Failure | 3 | I4 — no false green |
| 4 Gate | 4 | I5 |
| 5 Integration | 5 | I6 |
| 6 Admin screens | 6 | — not a DoD row |
| 7 Value | 7 | I7 |

## Recording medium — read this first

There is **no graphical admin UI** in the increment: `offer-management/` is a backend microservice and
the only UI artifact is a Storybook prototype (scenario 6). The API has no CORS headers and every
non-actuator path is behind a JWT, so an unauthenticated browser page cannot call it.

The recorder therefore renders the evidence it records live:

- **live HTTP** — real requests issued by the recorder against the live services at take time; the raw
  status line and the raw response body are displayed verbatim;
- **captured command output** — output of a real command run immediately before the take, displayed
  verbatim with the exact command and the capture time.

Nothing on screen is mocked, re-typed or summarised from memory. Where a scenario cannot be shown in a
browser at all, it is marked **NOT DEMONSTRATED** with the reason.

---

1. **Artifact** — the built service is running in the cluster and reports itself ready.
   - open `http://localhost:8080/actuator/health/readiness` → the live JSON body
   - show `kubectl -n offer-management get pods` output (captured) → 5 pods, each `1/1 Running`
   - **expected:** readiness body contains `"status":"UP"` and every pod line ends `1/1 Running`.

2. **Contract** — the element-02 endpoint set is exercised against the live service.
   - show the output of `bash offer-management/e2e/run-e2e.sh` (captured) → 41 request pairs executed
   - show the 22-row endpoint table (element 02) and the two known failures with their cause
   - issue one live unauthenticated `GET /products` → show the raw `401 UNAUTHENTICATED` body
   - issue one live authenticated `GET /products` as `carla` → show the raw `200` body
   - **expected:** 41 pairs executed, both failures are declared-`2xx` pairs returning `409`, and the two
     live calls return real `401` / `200` bodies.

3. **Failure** — the publish gate refuses a description that has no price.
   - live sequence with real Keycloak tokens: create product → save draft → upload photo → request review →
     approve as a second person → `POST /publications` with **no price scheduled**
   - show the raw response
   - **expected:** `422` with code `PUBLICATION_BLOCKED` and a populated `details.blocking` array
     containing `PRICE_REQUIRED`.

4. **Gate** — architecture and tests are green.
   - show the output of `cd offer-management && ./gradlew test --no-daemon --rerun-tasks` (captured)
   - show the aggregate parsed from `build/test-results/test/*.xml` and the per-context ArchUnit table
   - **expected:** `BUILD SUCCESSFUL`, 317 tests / 0 failures, 5 context `ArchitectureOf{Context}Test`
     classes green (30 rules).
   - **NOT DEMONSTRATED in a browser** — a Gradle run is a terminal/CI fact with no browser surface. It is
     shown as captured command output, not as a browser interaction.

5. **Integration** — a boundary event reaches the `browsing offer` consumer and its read model updates.
   - publish the scenario-3 product for real (price scheduled as `sara`), then read the consumer
   - show the live `GET http://localhost:18090/` body, filtered to this product
   - **expected:** the product's read-model line has `"present": true` and
     `"lastEvent": "ProductVersionPublishedToOffer_v1"`; the `ProductPricesChanged_v1` and
     `ProductRemovedFromOffer_v1` transitions are additionally evidenced by the captured
     `bash offer-management/e2e/run-integration.sh` run (`5 passed, 0 failed`).
   - **NOT shown as a browser page** — the consumer serves `Content-Type: application/x-ndjson`, which
     Chromium downloads instead of rendering, so its body is shown as the raw response of a live GET.

6. **Admin screens** — the Storybook prototype click-through.
   - open `http://localhost:6006/?path=/story/offer-management--catalog`
   - walk Catalog → Create → Draft → Review → Publish → Pricing → Versions through the in-page buttons
   - **expected:** each click navigates to the named screen inside the Storybook preview frame; the
     captured Storybook gate (`demo/evidence/storybook-gate.txt`) reports 9 stories / 4 flows green.

7. **Value** — I7's metric has no baseline.
   - **NOT DEMONSTRATED** — the spec metric ("share of drafts reaching `PUBLISHED` without a rejection
     round", baseline → target) has no recorded baseline anywhere in the repository and no instrumentation.
     There is nothing to show and no number is shown; the chapter states this explicitly.
   - To close: instrument the metric, capture a baseline over a real window, re-measure after the target window.

---

## Known contradictions carried into the demo (not hidden, not weakened)

- The authoritative `.hurl` suite declares 2 pairs as `2xx` that the domain rules make `409`
  (`offer-management/e2e/offer-management.hurl:250` — rejecting an already-decided review;
  `offer-management/e2e/offer-management.hurl:324` — re-publishing a frozen version). The suite therefore
  reports 39/41; all 16 declared error pairs pass.
- The plan brief counts "6 context ArchUnit suites"; the repository has **5**
  (`catalog`, `draft`, `offer`, `pricing`, `publishing`), 6 rules each.
- `docs/prove-of-done.md` contradicts itself on I1: the summary table says **PASS**
  (`docs/prove-of-done.md:11`) while the I1 body still says "**Result: FAIL.**"
  (`docs/prove-of-done.md:47-48`). The demo does not rely on either — its chapter 4 runs the spec's I5
  gate (`./gradlew test`). `docs/` is out of scope for this node, so the inconsistency is reported, not fixed.

## Chapter timestamps

The take's `mark()` timestamps are wall-clock relative to the start of the script; on the screencast
timeline the chapter card of a scenario appears ~3.5 s later (measured by decoding the recording and
comparing frames for chapters 1, 3, 6 and 7). `demo/chapters.json` therefore carries the measured +3.5 s
offset, and jumping to a chapter lands on its card. Alignment is re-checked after every take.

## How to re-run

```bash
# 1. stack up (app :8080, Keycloak :18081, consumer :18090)
#    see .agents/skills/run-offer-management/SKILL.md for the exact commands

# 2. Storybook prototype
(cd .storybook && npm install && npm run storybook)              # :6006

# 3. capture ALL evidence, incl. the Storybook gate (PW_NODE_PATH is required:
#    .storybook/package.json has no playwright dependency)
PW_NODE_PATH=/opt/homebrew/lib/node_modules/@playwright/cli/node_modules \
  bash demo/prepare-evidence.sh
(cd demo/evidence && python3 -m http.server 18099 --bind 127.0.0.1) &

# 4. record
playwright-cli open about:blank && playwright-cli resize 1280 720
playwright-cli --raw run-code --filename=demo/record.js > demo/out/raw-chapters.json

# 5. chapters — timestamp = mark() + 3.5 s offset, durationSec = ffprobe of the .webm
node ~/.agents/skills/demo/references/embed-chapters.mjs \
  demo/offer-management.webm demo/chapters.json demo/offer-management.chapters.mkv
```

`demo/prepare-evidence.sh` writes every file `demo/record.js` puts on screen:
`pods.txt`, `e2e.txt`, `storybook-gate.txt`, `integration.txt`, `gradle-test.txt`,
`tests-summary.json`, `photo.jpeg` — and re-establishes the consumer port-forward on `:18090`
(needed again after `run-integration.sh` tears its own down).
