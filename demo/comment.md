## Demo — offer-management implementation increment

Recorded against the live k3s-in-podman stack on branch `feat/offer-management`.
Artifacts and the full, reproducible procedure: `demo/` (see `demo/scenarios.md`).

### Video

- `demo/offer-management.chapters.mkv` — 2 min 23 s, **8 chapters** (one per scenario + intro)
- `demo/offer-management.webm` — raw screencast
- The `.mkv` cannot be attached through the GitHub CLI; play it locally
  (`mpv demo/offer-management.chapters.mkv`) — chapters are navigable in mpv/VLC.

| # | chapter | from |
|---|---|---|
| — | Intro | 0:00 |
| 1 | Artifact — the service in the cluster | 0:10 |
| 2 | Contract — the 22-endpoint API exercised | 0:20 |
| 3 | Failure — publish with no price | 0:39 |
| 4 | Gate — architecture and tests green | 1:03 |
| 5 | Integration — event reaches the consumer | 1:17 |
| 6 | Admin screens — Storybook prototype | 1:41 |
| 7 | Value — I7 has no baseline | 2:09 |

Chapter timestamps carry a measured +3.5 s screencast-startup offset, so jumping to a chapter lands on
its card (verified by decoding the recording).

### Scenario ↔ DoD-row mapping

The seven scenarios follow the plan brief's numbering, which is **not** the I1–I7 numbering of the spec
and `docs/prove-of-done.md`. The recording's closing summary prints this mapping:

| scenario | chapter | DoD row |
|---|---|---|
| 1 Artifact | 1 | I1 |
| 2 Contract | 2 | I2 (and I3 — the declared error pairs) |
| 3 Failure | 3 | I4 — no false green |
| 4 Gate | 4 | I5 |
| 5 Integration | 5 | I6 |
| 6 Admin screens | 6 | — not a DoD row |
| 7 Value | 7 | I7 |

### Public URLs (ephemeral Cloudflare quick tunnels)

| surface | URL | verified |
|---|---|---|
| Storybook prototype (the admin screens) | https://reveals-centers-listen-him.trycloudflare.com | `/` → 200, `/index.json` → 200, `/iframe.html?id=offer-management--catalog&viewMode=story` → 200 |
| offer-management app | https://poster-chronicles-rack-partnerships.trycloudflare.com | `/actuator/health/readiness` → 200 (`{"status":"UP"}`); `/` → 401 by design — every non-actuator path requires a JWT |

Both tunnels are **ephemeral**: they live only while the `cloudflared` processes run.
`ps -eo pid=,command= | grep "cloudflared tunnel --no-autoupdate"` currently shows
`87498` (app, `:8080`) and `87499` (Storybook, `:6006`); `kill 87498 87499` stops both.

### What each chapter actually proves

| chapter | evidence | how it is shown |
|---|---|---|
| 1 Artifact | readiness `UP`, 5/5 pods `Running` | live browser navigation + captured `kubectl get pods` |
| 2 Contract | 41 request pairs run, 39 pass; 22 declared endpoints; 16 declared error pairs | captured `bash offer-management/e2e/run-e2e.sh` + **live** `401`/`200` bodies |
| 3 Failure | `422 PUBLICATION_BLOCKED`, `details.blocking[].code=PRICE_REQUIRED` | **live** draft → photo → review → approval → publish, real Keycloak tokens |
| 4 Gate | 317 tests, 0 failures, 5 ArchUnit context suites (30 rules) | captured `./gradlew test --no-daemon --rerun-tasks` + results parsed from `build/test-results/test/*.xml` |
| 5 Integration | `ProductVersionPublishedToOffer_v1` in the consumer read model | **live** publish, then a live `GET http://localhost:18090/`; captured `run-integration.sh` (`5 passed, 0 failed`) |
| 6 Admin screens | 9 stories, 4 click-through flows, gate `QUALITY GATE PASSED` | live Storybook click-through + captured gate output |
| 7 Value | — | see below |

All captured evidence is committed under `demo/evidence/` (`pods.txt`, `e2e.txt`,
`storybook-gate.txt`, `integration.txt`, `gradle-test.txt`, `tests-summary.json`).

### Honest limitations (nothing weakened to make a row pass)

1. **No graphical admin UI exists.** The increment is a backend microservice; the only UI artifact is a
   Storybook prototype (chapter 6). Everything else is API/cluster evidence.
2. **Chapters 1, 2 (suite), 4 and 6 (gate) are captured command output, not browser interaction** — a
   `kubectl`, `hurl` or `gradle` run has no browser surface. The recorder has no shell access, so these
   are shown as the verbatim output of a real run (`demo/prepare-evidence.sh`, executed immediately
   before the take) and committed under `demo/evidence/`. Everything else is a live HTTP response
   issued while recording.
3. **The consumer read model is not shown as a page** — it serves `Content-Type: application/x-ndjson`,
   which Chromium downloads rather than renders. Its body is shown as the raw response of a live `GET`.
4. **The 2 failing hurl pairs are declared `2xx` pairs that the domain rules make `409`**
   (`offer-management/e2e/offer-management.hurl:250` — rejecting an already-decided review;
   `…:324` — re-publishing a frozen version). All 16 declared error pairs pass. Not hidden, not silenced.
5. **I7 is not measured.** The spec metric ("share of drafts reaching `PUBLISHED` without a rejection
   round", baseline → target within 60 days) has **no baseline** anywhere in the repository and no
   instrumentation (`docs/specs/offer-management/spec.md:229`, `:646`). Chapter 7 says exactly that and
   shows no number. To close: instrument the metric, capture a baseline over a real window, re-measure.
6. The plan brief counted **6** context ArchUnit suites; the repository has **5**
   (`catalog`, `draft`, `offer`, `pricing`, `publishing`), 6 rules each — 30 rules, all green.
7. **`docs/prove-of-done.md` contradicts itself on I1** — the summary table says **PASS**
   (`:11`) while the I1 body still says "**Result: FAIL.**" (`:47-48`). The demo does not rely on either
   (its chapter 4 runs the spec's I5 gate), and `docs/` was out of scope for this node. Reported, not fixed.

### Re-run

`demo/scenarios.md` ends with the exact command sequence (stack → Storybook + gate →
`PW_NODE_PATH=… bash demo/prepare-evidence.sh` → `playwright-cli run-code --filename=demo/record.js` →
chapter embed). The Storybook gate needs `PW_NODE_PATH` pointing at a `node_modules` containing
`playwright`: `.storybook/package.json` has no such dependency, so the previously documented
`NODE_PATH=.` form cannot run as written — that is a gap in `.storybook/`, not in the gate.
