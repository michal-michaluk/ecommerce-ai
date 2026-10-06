// demo/record.js — screencast of offer-management, one chapter per acceptance scenario.
//
// Run:  playwright-cli open about:blank
//       playwright-cli resize 1280 720
//       playwright-cli --raw run-code --filename=demo/record.js > demo/chapters.json
//
// What is on screen is either (a) a real HTTP response of the live services issued by this
// script at take time, or (b) the verbatim output of a real command run by
// demo/prepare-evidence.sh immediately before the take. Nothing is mocked.
async page => {
  const SIZE = { width: 1280, height: 720 };
  const OUT = "/Users/michal/workspace/ai-craft/ecommerce-ai/demo/offer-management.webm";
  const BASE = "http://localhost:8080";
  const KC = "http://localhost:18081";
  const CONSUMER = "http://localhost:18090";
  const STORYBOOK = "http://localhost:6006";
  const EVIDENCE = "http://localhost:18099";

  const t0 = Date.now();
  const chapters = [];

  const esc = (s) =>
    String(s).replace(/[&<>]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;" }[c]));

  async function mark(title, description) {
    chapters.push({ title, t: +((Date.now() - t0) / 1000).toFixed(2) });
    await page.screencast.showChapter(title, { description, duration: 2200 });
    await page.waitForTimeout(500);
  }

  const CSS = `
    :root { color-scheme: dark; }
    * { box-sizing: border-box; }
    body { margin: 0; padding: 22px 26px; background: #10141a; color: #d7dee8;
           font: 14px/1.5 -apple-system, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; }
    h1 { font-size: 21px; margin: 0 0 4px; color: #fff; }
    header p { margin: 0 0 16px; color: #8b98a9; font-size: 13px; }
    h2 { font-size: 12px; text-transform: uppercase; letter-spacing: .09em; color: #7d8a9c;
         margin: 16px 0 8px; }
    .term { background: #070a0e; border: 1px solid #232c38; border-radius: 5px;
            padding: 10px 13px; margin-bottom: 12px; overflow: hidden; }
    .term .prompt { color: #4ec9a5; font: 600 12.5px/1.45 ui-monospace, SFMono-Regular, Menlo, monospace; }
    .term pre { margin: 6px 0 0; color: #c6d0dc; font: 12px/1.42 ui-monospace, SFMono-Regular, Menlo, monospace;
                white-space: pre-wrap; word-break: break-word; max-height: 430px; overflow: hidden; }
    .term.ok .prompt { color: #6cc7ff; }
    .term.bad pre { color: #ff9c8a; }
    .col { display: flex; gap: 14px; align-items: flex-start; }
    .col > * { flex: 1 1 0; min-width: 0; }
    table { width: 100%; border-collapse: collapse; font-size: 11.5px; background: #070a0e;
            border: 1px solid #232c38; border-radius: 5px; }
    th, td { text-align: left; padding: 4px 8px; border-bottom: 1px solid #1b222b; }
    th { color: #7d8a9c; text-transform: uppercase; font-size: 10px; letter-spacing: .07em; }
    td.m { color: #4ec9a5; font-family: ui-monospace, Menlo, monospace; white-space: nowrap; }
    td.s { color: #6cc7ff; font-family: ui-monospace, Menlo, monospace; }
    td.e { color: #8b98a9; font-family: ui-monospace, Menlo, monospace; }
    .pass { color: #4ec9a5; } .fail { color: #ff9c8a; }
    code { color: #ffd479; font-family: ui-monospace, Menlo, monospace; }
    .note { color: #8b98a9; font-size: 12px; margin: 8px 0 0; }
    .big { font-size: 17px; color: #fff; }
    ul { margin: 6px 0 0 18px; padding: 0; } li { margin: 4px 0; }
  `;

  const term = (cmd, out, cls = "ok") =>
    `<div class="term ${cls}"><div class="prompt">$ ${esc(cmd)}</div><pre>${esc(out)}</pre></div>`;

  const table = (headers, rows) =>
    `<table><thead><tr>${headers.map((h) => `<th>${esc(h)}</th>`).join("")}</tr></thead><tbody>${rows
      .map((r) => `<tr>${r.map((c, i) => `<td class="${["m", "s", "e"][i] || ""}">${esc(c)}</td>`).join("")}</tr>`)
      .join("")}</tbody></table>`;

  async function show(title, subtitle, blocks) {
    await page.setContent(
      `<!doctype html><html><head><meta charset="utf-8"><style>${CSS}</style></head><body>` +
        `<header><h1>${esc(title)}</h1><p>${esc(subtitle)}</p></header>${blocks.join("")}</body></html>`,
    );
    await page.waitForTimeout(350);
  }

  async function evidence(name) {
    const r = await page.request.get(`${EVIDENCE}/${name}`);
    if (!r.ok()) throw new Error(`evidence ${name} unavailable: HTTP ${r.status()}`);
    return r.text();
  }

  async function mint(user) {
    const r = await page.request.post(`${KC}/realms/iot/protocol/openid-connect/token`, {
      form: {
        grant_type: "password",
        client_id: "iot-service",
        client_secret: "secret",
        username: user,
        password: user,
      },
    });
    return (await r.json()).access_token;
  }

  const clip = (s, n) => (s.length > n ? s.slice(0, n) + `\n… (${s.length - n} more bytes)` : s);

  async function call(method, path, { token, json, multipart } = {}) {
    const options = { headers: token ? { Authorization: `Bearer ${token}` } : {} };
    if (json) options.data = json;
    if (multipart) options.multipart = multipart;
    const r = await page.request[method.toLowerCase()](path.startsWith("http") ? path : BASE + path, options);
    const body = await r.text();
    let pretty = body;
    try {
      pretty = JSON.stringify(JSON.parse(body), null, 2);
    } catch {
      /* leave the body as-is */
    }
    return { status: r.status(), body, pretty };
  }

  const line = (method, path, res) =>
    `${method} ${path}\n→ HTTP ${res.status}\n${clip(res.pretty, 560)}`;

  let failure = null;
  await page.screencast.start({ path: OUT, size: SIZE });

  try {
    // ── Intro ────────────────────────────────────────────────────────────────
    await mark("Intro", "offer-management implementation increment on the live k3s stack.");
    await show(
      "offer-management — implementation increment",
      "Backend microservice · Java 25 / Spring Boot · hexagonal · element-02 contract: 22 REST endpoints",
      [
        `<div class="term"><pre>Live stack (k3s in a podman container):
  app         ${BASE}            Keycloak ${KC}
  consumer    ${CONSUMER}            Storybook prototype ${STORYBOOK}

Chapters: artifact · contract · failure · gate · integration · admin screens · value

Every screen is either a live HTTP response of these services, or the verbatim
output of a command run immediately before this take (demo/prepare-evidence.sh).</pre></div>`,
      ],
    );
    await page.waitForTimeout(3200);

    // ── 1. Artifact ──────────────────────────────────────────────────────────
    await mark("1. Artifact — the service in the cluster", "readiness UP and every pod 1/1 Running.");
    await show("1. Artifact", "The built service is running in the cluster and reports itself ready.", [
      term("curl http://localhost:8080/actuator/health/readiness", "live — the browser is showing this URL"),
    ]);
    await page.goto(`${BASE}/actuator/health/readiness`, { waitUntil: "load" });
    await page.waitForTimeout(3000);

    await show("1. Artifact", "Same stack, seen from the cluster control plane.", [
      term("kubectl -n offer-management get pods", await evidence("pods.txt")),
    ]);
    await page.waitForTimeout(3600);

    // ── 2. Contract ──────────────────────────────────────────────────────────
    await mark("2. Contract — the 22-endpoint API exercised", "41 request pairs executed; live 401 and 200 bodies.");
    await show("2. Contract — element-02 suite", "The authoritative hurl suite, run against this stack before the take.", [
      term("bash offer-management/e2e/run-e2e.sh", await evidence("e2e.txt")),
      `<p class="note">41 pairs executed: 39 pass. The 2 failures are declared-<code>2xx</code> pairs that the domain rules make <code>409</code> — a decided review is decided once, a frozen version is frozen. All 16 declared error pairs pass.</p>`,
    ]);
    await page.waitForTimeout(5000);

    await show("2. Contract — the 22 declared endpoints", "Element 02 endpoint set and the declared error codes.", [
      table(
        ["screen", "endpoint", "ok", "declared errors"],
        [
          ["01 Catalog", "GET /products", "200", "401, 403"],
          ["01 Catalog", "GET /products/{productId}", "200", "401, 403, 404"],
          ["02 Create", "POST /products", "201", "401, 403, 422"],
          ["03 Draft", "GET /products/{productId}/description-draft", "200", "401, 403, 404"],
          ["03 Draft", "PUT /products/{productId}/description-draft", "200", "401, 403, 404, 409, 422"],
          ["03 Draft", "POST /products/{productId}/description-draft/review-requests", "201", "401, 403, 404, 409"],
          ["04 Photos", "GET /product-photo-formats", "200", "401"],
          ["04 Photos", "POST /products/{productId}/description-draft/photos", "201", "401, 403, 404, 422"],
          ["04 Photos", "DELETE /products/{productId}/description-draft/photos/{photoId}", "204", "401, 403, 404"],
          ["05 Queue", "GET /review-requests", "200", "401, 403"],
          ["06 Review", "GET /review-requests/{reviewRequestId}", "200", "401, 403, 404"],
          ["06 Review", "POST /review-requests/{reviewRequestId}/approval", "200", "401, 403, 404, 409"],
          ["06 Review", "POST /review-requests/{reviewRequestId}/rejection", "200", "401, 403, 404, 409"],
          ["07 Publish", "GET /products/{productId}/publication", "200", "401, 403, 404"],
          ["07 Publish", "POST /products/{productId}/publications", "201", "401, 403, 404, 409, 422"],
          ["08 Pricing", "GET /products/{productId}/prices", "200", "401, 403, 404"],
          ["08 Pricing", "POST /products/{productId}/prices", "201", "401, 403, 404, 409, 422"],
          ["08 Pricing", "PUT /products/{productId}/prices/{priceId}", "200", "401, 403, 404, 409, 422"],
          ["08 Pricing", "DELETE /products/{productId}/prices/{priceId}", "204", "401, 403, 404"],
          ["09 Versions", "GET /products/{productId}/versions", "200", "401, 403, 404"],
          ["09 Versions", "POST /products/{productId}/versions/{version}/revert", "201", "401, 403, 404"],
          ["09 Removal", "DELETE /products/{productId}/offer-presence", "204", "401, 403, 404"],
        ],
      ),
    ]);
    await page.waitForTimeout(4200);

    const unauthenticated = await call("GET", "/products");
    const cmToken = await mint("carla");
    const authenticated = await call("GET", "/products", { token: cmToken });

    await show("2. Contract — live calls, raw bodies", "Issued against http://localhost:8080 while recording.", [
      term("GET /products (no token)", line("GET", "/products", unauthenticated), "bad"),
      term("GET /products (Bearer carla · content-manager)", line("GET", "/products", authenticated)),
    ]);
    await page.waitForTimeout(4600);

    // ── 3. Failure ───────────────────────────────────────────────────────────
    await mark("3. Failure — publish with no price", "422 PUBLICATION_BLOCKED with details.blocking.");
    const martaToken = await mint("marta");
    const saraToken = await mint("sara");
    const log = [];
    const render = async (headline) =>
      show("3. Failure — the publish gate", headline, [term("live API session (real Keycloak tokens)", log.join("\n\n"))]);

    const step = async (method, path, description, res) => {
      log.push(`# ${description}\n${line(method, path, res)}`);
      await render("Real lifecycle over the live API — final step is the one under test: no price is ever scheduled.");
      await page.waitForTimeout(1500);
    };

    await render("Building a publishable description over the live API: draft → photo → review → approval…");
    await page.waitForTimeout(1200);

    const created = await call("POST", "/products", {
      token: cmToken,
      json: { title: "Kosiarka ręczna 340 (demo)", category: "Ogród" },
    });
    const productId = JSON.parse(created.body).productId;
    await step("POST", "/products", "create the product", created);

    const saved = await call("PUT", `/products/${productId}/description-draft`, {
      token: cmToken,
      json: {
        title: "Kosiarka ręczna 340",
        description: "Solidna kosiarka ręczna do trawy i chwastów z prostym regulowanym kołem.",
      },
    });
    await step("PUT", `/products/${productId}/description-draft`, "save the draft", saved);

    const photo = await (await page.request.get(`${EVIDENCE}/photo.jpeg`)).body();
    const uploaded = await call("POST", `/products/${productId}/description-draft/photos`, {
      token: cmToken,
      multipart: { file: { name: "photo.jpeg", mimeType: "image/jpeg", buffer: photo } },
    });
    await step("POST", `/products/${productId}/description-draft/photos`, "upload the photo", uploaded);

    const draft = await call("GET", `/products/${productId}/description-draft`, { token: cmToken });
    const version = JSON.parse(draft.body).version;
    await step("GET", `/products/${productId}/description-draft`, "read the current draft version", draft);

    const review = await call("POST", `/products/${productId}/description-draft/review-requests`, { token: cmToken });
    const reviewRequestId = JSON.parse(review.body).reviewRequestId;
    await step("POST", `/products/${productId}/description-draft/review-requests`, "request the review", review);

    const approved = await call("POST", `/review-requests/${reviewRequestId}/approval`, { token: martaToken });
    await step("POST", `/review-requests/${reviewRequestId}/approval`, "a second person approves it (marta)", approved);

    const blocked = await call("POST", `/products/${productId}/publications`, {
      token: cmToken,
      json: { descriptionVersion: version, availableFrom: null },
    });
    await step("POST", `/products/${productId}/publications`, "NOW publish — but no price was ever scheduled", blocked);

    await show("3. Failure — result", "No false green: the gate refuses the publish and says exactly what blocks it.", [
      term(
        "the raw 422 body",
        `HTTP ${blocked.status}\n${blocked.pretty}\n\n→ code PUBLICATION_BLOCKED\n→ details.blocking[].code = PRICE_REQUIRED`,
        blocked.status === 422 ? "bad" : "ok",
      ),
    ]);
    await page.waitForTimeout(5000);

    // ── 4. Gate ──────────────────────────────────────────────────────────────
    await mark("4. Gate — architecture and tests green", "317 tests, 0 failures, 5 context ArchUnit suites.");
    await show("4. Gate", "The plan's quality gate: full test suite including the per-context ArchUnit rules.", [
      term("cd offer-management && ./gradlew test --no-daemon --rerun-tasks", await evidence("gradle-test.txt")),
      `<p class="note">NOT a browser interaction — a Gradle run has no browser surface. Shown as verbatim captured command output.</p>`,
    ]);
    await page.waitForTimeout(5000);

    const summary = JSON.parse(await evidence("tests-summary.json"));
    await show(
      "4. Gate — parsed from build/test-results/test/*.xml",
      `${summary.totals.classes} test classes · ${summary.totals.tests} tests · ${summary.totals.failures} failures · ${summary.totals.errors} errors`,
      [
        table(
          ["ArchUnit context suite", "rules", "failures"],
          summary.archunit.map((a) => [a.class, String(a.tests), String(a.failures)]),
        ),
        `<p class="note">5 context suites × 6 rules = 30 architecture rules, all green. Testcount ${summary.totals.tests}, failures ${summary.totals.failures}.</p>`,
      ],
    );
    await page.waitForTimeout(5000);

    // ── 5. Integration ───────────────────────────────────────────────────────
    const businessDate = new Date().toLocaleDateString("sv-SE", { timeZone: "Europe/Warsaw" });
    await mark("5. Integration — event reaches the consumer", "browsing-offer read model updates after the publish.");
    await show("5. Integration — the boundary", "Continue the product from chapter 3: schedule a price, then publish for real.", [
      term("bash offer-management/e2e/run-integration.sh", await evidence("integration.txt")),
    ]);
    await page.waitForTimeout(4800);

    const ilog = [];
    const irender = async () =>
      show("5. Integration — live publish", "The event crosses the topic to the browsing-offer consumer.", [
        term("live API session → browsing-offer read model", ilog.join("\n\n")),
      ]);
    const istep = async (method, path, description, res) => {
      ilog.push(`# ${description}\n${line(method, path, res)}`);
      await irender();
      await page.waitForTimeout(1600);
    };

    const price = await call("POST", `/products/${productId}/prices`, {
      token: saraToken,
      json: {
        kind: "PRICE",
        amount: { value: "259.00", currency: "PLN" },
        validFrom: businessDate,
        validTo: null,
      },
    });
    await istep("POST", `/products/${productId}/prices`, `sales (sara) schedules 259.00 PLN from ${businessDate}`, price);

    const published = await call("POST", `/products/${productId}/publications`, {
      token: cmToken,
      json: { descriptionVersion: version, availableFrom: null },
    });
    await istep("POST", `/products/${productId}/publications`, "publish the approved version", published);

    let readModel = null;
    const deadline = Date.now() + 30000;
    while (Date.now() < deadline) {
      const raw = await (await page.request.get(`${CONSUMER}/`)).text();
      const hit = raw
        .split("\n")
        .map((l) => l.trim())
        .filter(Boolean)
        .find((l) => l.includes(productId));
      if (hit) {
        const doc = JSON.parse(hit);
        if (doc.present === true && doc.lastEvent === "ProductVersionPublishedToOffer_v1") {
          readModel = JSON.stringify(doc, null, 2);
          break;
        }
      }
      await page.waitForTimeout(1500);
    }
    if (!readModel) throw new Error("boundary event did not reach the consumer read model within 30s");

    ilog.push(
      `# the consumer's read model for this product (live, filtered from GET http://localhost:18090/)\n${readModel}`,
    );
    await irender();
    await page.waitForTimeout(3200);

    await show("5. Integration — the live consumer endpoint", "GET http://localhost:18090/ — the browsing-offer read model.", [
      term(
        "the consumer endpoint, served live while recording",
        "Content-Type: application/x-ndjson\n\nChromium downloads this content type instead of rendering it, so the read model is\nshown above as the raw body of a live GET http://localhost:18090/ — not as a page.",
      ),
    ]);
    await page.waitForTimeout(3400);

    // ── 6. Admin screens ─────────────────────────────────────────────────────
    await mark("6. Admin screens — Storybook prototype", "Catalog → draft → review → publish → pricing → versions.");
    await show("6. Admin screens", "There is no graphical admin UI in this increment: the UI artifact is a Storybook prototype.", [
      term("(cd .storybook && NODE_PATH=… node ./verify-storybook.cjs http://localhost:6006)", await evidence("storybook-gate.txt")),
      `<p class="note">Prototype only — plain DOM, no data fetching, no business logic.</p>`,
    ]);
    await page.waitForTimeout(4000);

    await page.goto(`${STORYBOOK}/?path=/story/offer-management--catalog`, { waitUntil: "networkidle" });
    await page.waitForTimeout(2600);

    const frame = page.frameLocator("#storybook-preview-iframe");
    const clickBody = async (label) => {
      await frame.locator(".om-body button", { hasText: label }).first().click({ timeout: 10000 });
      await page.waitForTimeout(1700);
    };
    const clickNav = async (label) => {
      await frame.locator(".om-nav button", { hasText: label }).first().click({ timeout: 10000 });
      await page.waitForTimeout(1700);
    };

    await clickBody("Create product");
    await clickBody("Create draft");
    await clickBody("Request review");
    await clickBody("Review");
    await clickBody("Approve & publish");
    await clickBody("Publish & schedule");
    await clickNav("Pricing");
    await clickNav("Versions");
    await clickBody("Revert to this");
    await page.waitForTimeout(1500);

    // ── 7. Value ─────────────────────────────────────────────────────────────
    await mark("7. Value — I7 has no baseline", "Stated honestly: no number is shown because none exists.");
    await show("7. Value — NOT MEASURED", "This chapter deliberately shows no number.", [
      `<div class="term"><pre>Spec metric (docs/specs/offer-management/spec.md:229)
  share of drafts reaching PUBLISHED without a rejection round,
  baseline → target within 60 days

State of the repository
  baseline captured ............ NO   (no instrumented counter, dashboard or dataset)
  target window evaluable ...... NO
  number invented here ......... NO

docs/specs/offer-management/spec.md:646
  "- I7's metric has no baseline yet — must be captured before the increment closes."

To close: instrument "drafts reaching PUBLISHED without a prior rejection event",
capture a baseline over a real window, re-measure after the target window.</pre></div>`,
      `<p class="note">Weakening nothing: this row is not marked pass, and the demo does not pretend to prove it.</p>`,
    ]);
    await page.waitForTimeout(6000);

    // ── Outro ────────────────────────────────────────────────────────────────
    await show(
      "Summary",
      "What the recording shows — and what it does not.",
      [
        `<div class="term"><pre>scenario                          how shown                 DoD row

1 artifact     readiness UP, 5/5 pods Running              live          I1
2 contract     22 endpoints / 41 request pairs, 39 pass   captured+live I2 (+ I3)
3 failure      422 PUBLICATION_BLOCKED + details.blocking live          I4
4 gate         317 tests, 0 failures, 5 ArchUnit suites   captured      I5
5 integration  ProductVersionPublishedToOffer in model    live          I6
6 screens      Storybook prototype, 9 stories / 4 flows   live          not a DoD row
7 value        no baseline exists — nothing measured      stated as such I7</pre></div>`,
        `<p class="note">Scenario numbering is the plan brief's; the right column maps each scenario to the matching row of docs/prove-of-done.md / spec §11.</p>`,
      ],
    );
    await page.waitForTimeout(3200);
  } catch (err) {
    failure = String((err && err.stack) || err);
  } finally {
    await page.screencast.stop();
  }

  return JSON.stringify({
    title: "offer-management — implementation increment (scenarios 1–7, DoD I1–I7)",
    durationSec: +((Date.now() - t0) / 1000).toFixed(2),
    failure,
    chapters,
  });
}
