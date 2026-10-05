// Low-fidelity prototype shell for the offer-management UI mockups.
// Plain DOM helpers: no design system, no data fetching, no business logic.

export const STORIES = {
  catalog: 'offer-management--catalog',
  create: 'offer-management--create',
  draft: 'offer-management--draft',
  photos: 'offer-management--photos',
  queue: 'offer-management--queue',
  review: 'offer-management--review',
  publish: 'offer-management--publish',
  pricing: 'offer-management--pricing',
  versions: 'offer-management--versions',
};

export function navigateTo(id) {
  const api = window.__STORYBOOK_CLIENT_API__;
  if (api && typeof api.selectStory === 'function') {
    try {
      api.selectStory(id);
      return;
    } catch (err) {
      /* fall through to URL navigation */
    }
  }
  window.parent.location.search = `?path=/story/${id}`;
}

const CSS = `
.om { font: 14px/1.45 -apple-system, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; color: #1f2328; background: #f6f7f9; min-height: 100vh; }
.om * { box-sizing: border-box; }
.om-top { background: #2b3138; color: #fff; padding: 10px 16px; display: flex; align-items: baseline; gap: 12px; }
.om-top a { color: #fff; text-decoration: none; }
.om-brand { font-weight: 700; letter-spacing: .2px; }
.om-tag { font-size: 11px; text-transform: uppercase; letter-spacing: .08em; background: #4b545e; padding: 2px 6px; border-radius: 3px; }
.om-nav { display: flex; gap: 2px; background: #e6e8eb; border-bottom: 1px solid #cfd3d7; padding: 0 16px; flex-wrap: wrap; }
.om-nav button { font: inherit; background: none; border: 0; border-bottom: 2px solid transparent; padding: 9px 12px; cursor: pointer; color: #444c56; }
.om-nav button:hover { background: #dde1e5; }
.om-nav button[data-active="true"] { border-bottom-color: #2b3138; color: #1f2328; font-weight: 600; }
.om-body { padding: 18px 16px 40px; max-width: 1120px; }
.om-screen-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 4px; }
.om-screen-head h1 { font-size: 20px; margin: 0 0 4px; }
.om-screen-head p { margin: 0; color: #59636e; }
.om-card { background: #fff; border: 1px solid #d8dce0; border-radius: 4px; padding: 16px; margin-bottom: 16px; }
.om-card > h2 { font-size: 15px; margin: 0 0 12px; text-transform: uppercase; letter-spacing: .04em; color: #59636e; }
.om-grid { display: grid; grid-template-columns: 1fr 300px; gap: 16px; align-items: start; }
.om-row { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.om-col { display: flex; flex-direction: column; gap: 6px; margin-bottom: 12px; }
label.om-lbl { font-weight: 600; font-size: 12px; color: #444c56; }
.om-in, .om-ta, .om-sel { font: inherit; border: 1px solid #c6cbd1; border-radius: 3px; padding: 7px 9px; background: #fff; width: 100%; }
.om-ta { min-height: 96px; resize: vertical; }
.om-hint { font-size: 12px; color: #59636e; }
.om-btn { font: inherit; border: 1px solid #b7bcc2; background: #fff; border-radius: 3px; padding: 7px 14px; cursor: pointer; }
.om-btn:hover { background: #f0f2f4; }
.om-btn.primary { background: #1f6feb; border-color: #1a5fd0; color: #fff; font-weight: 600; }
.om-btn.primary:hover { background: #1a5fd0; }
.om-btn.danger { color: #b42318; border-color: #e2b3ae; }
.om-btn[disabled] { opacity: .5; cursor: not-allowed; }
.om-tbl { width: 100%; border-collapse: collapse; font-size: 13px; }
.om-tbl th, .om-tbl td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #e4e7ea; }
.om-tbl th { background: #f0f2f4; font-size: 12px; text-transform: uppercase; letter-spacing: .04em; color: #59636e; }
.om-tbl tbody tr:hover { background: #fafbfc; }
.om-badge { display: inline-block; font-size: 11px; font-weight: 600; padding: 2px 7px; border-radius: 10px; border: 1px solid transparent; }
.om-badge.draft { background: #eef1f4; color: #444c56; border-color: #d8dce0; }
.om-badge.review { background: #fff4d6; color: #7a4b00; border-color: #f0d79a; }
.om-badge.published { background: #e2f3e8; color: #1a6438; border-color: #b6e0c5; }
.om-badge.scheduled { background: #e7f0ff; color: #1a4fa0; border-color: #bcd3f7; }
.om-badge.blocked { background: #fdeceb; color: #b42318; border-color: #f2c3bf; }
.om-badge.removed { background: #f0f2f4; color: #6b7280; border-color: #d8dce0; }
.om-banner { border-radius: 4px; padding: 10px 12px; margin-bottom: 14px; font-size: 13px; border: 1px solid; }
.om-banner.warn { background: #fff9e8; border-color: #f0d79a; color: #6b4b00; }
.om-banner.err { background: #fdeceb; border-color: #f2c3bf; color: #8f1d14; }
.om-banner.ok { background: #e9f7ef; border-color: #b6e0c5; color: #1a6438; }
.om-banner b { display: block; margin-bottom: 2px; }
.om-missing { margin: 6px 0 0; padding-left: 18px; }
.om-missing li { margin: 2px 0; }
.om-missing li.done { color: #1a6438; list-style: none; margin-left: -18px; }
.om-missing li.done::before { content: "✓ "; }
.om-missing li.todo::before { content: "× "; color: #b42318; font-weight: 700; }
.om-thumbs { display: flex; gap: 10px; flex-wrap: wrap; }
.om-thumb { width: 108px; height: 108px; border: 1px dashed #c6cbd1; border-radius: 3px; background: #fafbfc; display: flex; align-items: center; justify-content: center; font-size: 11px; color: #59636e; text-align: center; padding: 6px; }
.om-thumb.filled { border-style: solid; background: #eef1f4; }
.om-thumb.add { border-style: solid; cursor: pointer; }
.om-actions { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 4px; }
.om-kv { display: grid; grid-template-columns: 180px 1fr; gap: 6px 12px; font-size: 13px; }
.om-kv dt { color: #59636e; }
.om-kv dd { margin: 0; }
.om-note { font-size: 12px; color: #59636e; margin-top: 8px; font-style: italic; }
.om-range { display: flex; gap: 6px; align-items: center; }
.om-range .om-in { width: 150px; }
`;

export function page({ active, body }) {
  const nav = [
    ['Catalog', STORIES.catalog],
    ['Create', STORIES.create],
    ['Draft', STORIES.draft],
    ['Photos', STORIES.photos],
    ['Review', STORIES.queue],
    ['Publish', STORIES.publish],
    ['Pricing', STORIES.pricing],
    ['Versions', STORIES.versions],
  ];

  const root = document.createElement('div');
  root.className = 'om';
  const style = document.createElement('style');
  style.textContent = CSS;
  root.appendChild(style);

  const top = document.createElement('div');
  top.className = 'om-top';
  top.innerHTML = '<span class="om-brand">Offer Management</span><span class="om-tag">low-fi mockup</span>';
  root.appendChild(top);

  const navEl = document.createElement('div');
  navEl.className = 'om-nav';
  nav.forEach(([label, id]) => {
    const b = document.createElement('button');
    b.textContent = label;
    if (id === active) b.dataset.active = 'true';
    b.addEventListener('click', () => navigateTo(id));
    navEl.appendChild(b);
  });
  root.appendChild(navEl);

  const bodyEl = document.createElement('div');
  bodyEl.className = 'om-body';
  if (typeof body === 'string') bodyEl.innerHTML = body;
  else bodyEl.appendChild(body);
  root.appendChild(bodyEl);

  return root;
}

export function head(title, subtitle, actions = '') {
  const d = document.createElement('div');
  d.className = 'om-screen-head';
  d.innerHTML = `<div><h1>${title}</h1><p>${subtitle}</p></div><div class="om-row">${actions}</div>`;
  return d;
}

export function card(title, inner) {
  const d = document.createElement('div');
  d.className = 'om-card';
  if (title) d.innerHTML = `<h2>${title}</h2>`;
  if (typeof inner === 'string') d.insertAdjacentHTML('beforeend', inner);
  else d.appendChild(inner);
  return d;
}

export function button(label, { variant = '', disabled = false, to = null, onClick = null } = {}) {
  const b = document.createElement('button');
  b.className = `om-btn ${variant}`.trim();
  b.textContent = label;
  b.disabled = disabled;
  if (to) b.addEventListener('click', () => navigateTo(to));
  if (onClick) b.addEventListener('click', onClick);
  return b;
}

export function goto(label, to, variant = '') {
  return button(label, { to, variant });
}

export function table(headers, rows) {
  const t = document.createElement('table');
  t.className = 'om-tbl';
  t.innerHTML =
    `<thead><tr>${headers.map((h) => `<th>${h}</th>`).join('')}</tr></thead>` +
    `<tbody>${rows.map((r) => `<tr>${r.map((c) => `<td>${c}</td>`).join('')}</tr>`).join('')}</tbody>`;
  return t;
}

export function badge(text, kind) {
  return `<span class="om-badge ${kind}">${text}</span>`;
}
