/**
 * Storybook quality gate — equivalent of
 * skill `specification` references/verify-storybook.cdp.js, adapted to Storybook 8/10.
 *
 * Why a local gate: the shared script (a) enumerates `#storybook-explorer-tree a`,
 * which no longer exists (SB10 renders buttons), and (b) checks `#storybook-root`
 * on the *manager* page, while stories render inside `#storybook-preview-iframe`.
 * This gate visits the real preview surface (`iframe.html?id=<storyId>`) per story.
 *
 * Usage: NODE_PATH=. node .storybook/verify-storybook.cjs [url]
 * Exits 0 only if every story renders, is non-empty, and every flow step navigates.
 */
const { chromium } = require('playwright');

const BASE = process.argv[2] || 'http://localhost:6007';

const FLOWS = [
  {
    name: 'happy path: catalog → create → draft → review → publish',
    start: 'offer-management--catalog',
    steps: [
      { click: 'Create product', expect: 'offer-management--create' },
      { click: 'Create draft', expect: 'offer-management--draft' },
      { click: 'Request review', expect: 'offer-management--queue' },
      { click: 'Review', expect: 'offer-management--review' },
      { click: 'Approve & publish', expect: 'offer-management--publish' },
      { click: 'Publish & schedule', expect: 'offer-management--catalog' },
    ],
  },
  {
    name: 'photos and pricing branches',
    start: 'offer-management--draft',
    steps: [
      { click: 'Add photo', expect: 'offer-management--photos', scope: 'body' },
      { click: 'Request review', expect: 'offer-management--queue', scope: 'body' },
      { click: 'Pricing', expect: 'offer-management--pricing', scope: 'nav' },
      { click: 'Save & schedule', expect: 'offer-management--catalog', scope: 'body' },
    ],
  },
  {
    name: 'reject and revert',
    start: 'offer-management--review',
    steps: [
      { click: 'Reject', expect: 'offer-management--draft', scope: 'body' },
      { click: 'Versions', expect: 'offer-management--versions', scope: 'nav' },
      { click: 'Revert to this', expect: 'offer-management--draft', scope: 'body' },
    ],
  },
  {
    name: 'remove product from offer',
    start: 'offer-management--versions',
    steps: [{ click: 'Remove from offer', expect: 'offer-management--catalog', scope: 'body' }],
  },
];

async function verifyStories(page) {
  const index = await (await fetch(`${BASE}/index.json`)).json();
  const stories = Object.values(index.entries || {}).filter((e) => e.type === 'story');

  const results = [];
  for (const story of stories) {
    const seen = [];
    const onError = (e) => seen.push(e);
    page.on('pageerror', (e) => onError('CRASH ' + e.message));
    const consoleHandler = (m) => {
      if (m.type() === 'error') onError('CONSOLE ' + m.text());
    };
    const responseHandler = (r) => {
      if (r.status() >= 400 && r.url().startsWith(BASE)) onError(`HTTP ${r.status()} ${r.url()}`);
    };
    page.on('console', consoleHandler);
    page.on('response', responseHandler);

    try {
      await page.goto(`${BASE}/iframe.html?id=${story.id}&viewMode=story`, {
        waitUntil: 'networkidle',
        timeout: 20000,
      });
      await page.waitForTimeout(600);
      const root = await page.$('#storybook-root');
      const content = root ? (await root.innerHTML()).trim() : '';
      results.push({
        name: `${story.title} / ${story.name}`,
        ok: seen.length === 0 && content.length > 0,
        errors: seen,
        empty: content.length === 0,
      });
    } catch (err) {
      results.push({ name: story.id, ok: false, errors: ['NAV ' + err.message], empty: true });
    } finally {
      page.off('console', consoleHandler);
      page.off('response', responseHandler);
      page.off('pageerror', onError);
    }
  }
  return results;
}

async function runFlows(page) {
  const results = [];
  for (const flow of FLOWS) {
    const errors = [];
    await page.goto(`${BASE}/?path=/story/${flow.start}`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(800);
    const frame = page.frameLocator('#storybook-preview-iframe');
    for (const [i, step] of flow.steps.entries()) {
      try {
        const scope = step.scope === 'nav' ? '.om-nav' : '.om-body';
        await frame.locator(`${scope} button`, { hasText: step.click }).first().click({ timeout: 5000 });
      } catch (err) {
        errors.push(`step ${i + 1}: could not click "${step.click}" — ${err.message.split('\n')[0]}`);
        break;
      }
      await page.waitForTimeout(700);
      if (!page.url().includes(step.expect)) {
        errors.push(`step ${i + 1}: expected url to contain "${step.expect}", got "${page.url()}"`);
        break;
      }
    }
    results.push({ name: flow.name, ok: errors.length === 0, errors });
  }
  return results;
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();

  console.log(`\n📖 Quality gate — ${BASE}\n`);
  const stories = await verifyStories(page);
  const flows = await runFlows(page);
  await browser.close();

  const storyFailed = stories.filter((s) => !s.ok);
  const flowFailed = flows.filter((f) => !f.ok);

  console.log(`📊 STORIES: ${stories.length - storyFailed.length} passed, ${storyFailed.length} failed`);
  for (const s of storyFailed) {
    console.log(`  ❌ ${s.name}${s.empty ? ' (empty story)' : ''}`);
    s.errors.forEach((e) => console.log(`     ${e}`));
  }
  console.log(`📊 FLOWS:   ${flows.length - flowFailed.length} passed, ${flowFailed.length} failed`);
  for (const f of flowFailed) {
    console.log(`  ❌ ${f.name}`);
    f.errors.forEach((e) => console.log(`     ${e}`));
  }
  console.log(storyFailed.length === 0 && flowFailed.length === 0 ? '\n✅ QUALITY GATE PASSED' : '\n❌ QUALITY GATE FAILED');
  process.exit(storyFailed.length === 0 && flowFailed.length === 0 ? 0 : 1);
})();
