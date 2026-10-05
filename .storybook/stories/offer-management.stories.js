import { STORIES, page, head, card, button, goto, table, badge } from './shell.js';

export default {
  title: 'Offer Management',
};

const openDraft = 'Prosto z półki / Kosiarka ręczna 340';

const Catalog = {
  name: '01 Product Catalog',
  render: () =>
    page({
      active: STORIES.catalog,
      body: (() => {
        const wrap = document.createElement('div');
        wrap.appendChild(
          head(
            'Product catalog',
            'All products of this offer. Status decides what the customer can see.',
            '',
          ),
        );
        wrap.querySelector('.om-row').appendChild(goto('+ Create product', STORIES.create, 'primary'));
        wrap.appendChild(
          card(
            null,
            table(
              ['Product', 'Description', 'Prices', 'Availability', 'Status', ''],
              [
                [
                  openDraft,
                  'v3 draft, v2 visible',
                  '1 active, 1 scheduled',
                  'from 2019-07-01',
                  badge('PUBLISHED', 'published'),
                  `<button class="om-btn om-open">Open</button>`,
                ],
                [
                  'Miotła ogrodowa 120',
                  'v1 in review',
                  '—',
                  '—',
                  badge('PENDING_REVIEW', 'review'),
                  `<button class="om-btn om-open">Open</button>`,
                ],
                [
                  'Grill ogrodowy 44',
                  'v2 scheduled',
                  '1 scheduled',
                  'from 2019-08-15',
                  badge('SCHEDULED', 'scheduled'),
                  `<button class="om-btn om-open">Open</button>`,
                ],
                [
                  'Stół piknikowy',
                  'v1 approved, gate blocked',
                  'price missing',
                  '—',
                  badge('BLOCKED', 'blocked'),
                  `<button class="om-btn om-open">Open</button>`,
                ],
                [
                  'Nowy produkt',
                  'v1 draft',
                  '—',
                  '—',
                  badge('DRAFT', 'draft'),
                  `<button class="om-btn om-open">Open</button>`,
                ],
                [
                  'Fotel plażowy',
                  'v4 published',
                  '1 expired',
                  '—',
                  badge('REMOVED', 'removed'),
                  `<button class="om-btn om-open">Open</button>`,
                ],
              ],
            ),
          ),
        );
        wrap.querySelectorAll('.om-open').forEach((b, i) =>
          b.addEventListener('click', () => navigate(i)),
        );
        function navigate(i) {
          const target = i === 3 ? STORIES.review : STORIES.draft;
          import('./shell.js').then((m) => m.navigateTo(target));
        }
        return wrap;
      })(),
    }),
};

const Create = {
  name: '02 Create Product',
  render: () =>
    page({
      active: STORIES.create,
      body: (() => {
        const wrap = document.createElement('div');
        wrap.appendChild(
          head('Create product', 'Only a title and an identifier are needed to start.'),
        );
        wrap.appendChild(
          card(
            'New product',
            `
          <div class="om-col"><label class="om-lbl">Product title</label><input class="om-in" value="Nowy produkt"></div>
          <div class="om-col"><label class="om-lbl">Product ID</label><input class="om-in" value="SKU-2019-0442" readonly></div>
          <div class="om-col"><label class="om-lbl">Category</label><select class="om-sel"><option>Zabawki</option><option>Ogród</option></select></div>
          <p class="om-hint">The title can be changed at any time — it is not binding at this stage.</p>
          <p class="om-note">Creates a blank draft. Nothing becomes visible to the customer.</p>`,
          ),
        );
        const actions = document.createElement('div');
        actions.className = 'om-actions';
        actions.appendChild(goto('Create draft', STORIES.draft, 'primary'));
        actions.appendChild(goto('Cancel', STORIES.catalog));
        wrap.appendChild(actions);
        return wrap;
      })(),
    }),
};

const Draft = {
  name: '03 Draft Workspace',
  render: () =>
    page({
      active: STORIES.draft,
      body: (() => {
        const wrap = document.createElement('div');
        wrap.appendChild(
          head(openDraft, 'Draft v3 (EDITING) — private until published. Nobody outside sees these changes.', badge('EDITING', 'draft')),
        );
        wrap.appendChild(
          card(
            null,
            `<div class="om-banner warn"><b>This draft is not complete yet</b>Fill the missing items before requesting a review.</div>`,
          ),
        );

        const grid = document.createElement('div');
        grid.className = 'om-grid';

        grid.appendChild(
          card(
            'Description',
            `
          <div class="om-col"><label class="om-lbl">Title</label><input class="om-in" value="${openDraft}"></div>
          <div class="om-col"><label class="om-lbl">Description</label><textarea class="om-ta">Solidna kosiarka ręczna do trawy i chwastów. Szerokość robocza 34 cm, wysokość cięcia regulowana w 4 stopniach.</textarea></div>
          <div class="om-col"><label class="om-lbl">Manual / warranty (optional)</label><input class="om-in" placeholder="Not provided"></div>
          <div class="om-actions"></div>`,
          ),
        );

        grid.appendChild(
          card(
            'What is missing',
            `<ul class="om-missing">
           <li class="done">Title</li>
           <li class="done">Description</li>
           <li class="done">At least one photo</li>
           <li class="todo">Price valid for a date range</li>
         </ul>
         <p class="om-note">Feedback is shown from the blank draft onward.</p>`,
          ),
        );

        const draftActions = grid.querySelector('.om-actions');
        draftActions.appendChild(goto('Add photo', STORIES.photos));
        draftActions.appendChild(goto('Set price', STORIES.pricing));
        draftActions.appendChild(goto('Request review →', STORIES.queue, 'primary'));

        wrap.appendChild(grid);
        wrap.appendChild(
          card(
            null,
            `<p class="om-hint">Requesting a review with missing items is allowed — the reviewer sees them. <b>Publishing</b> is not: the quality gate blocks it.</p>`,
          ),
        );
        return wrap;
      })(),
    }),
};

const Photos = {
  name: '04 Photos',
  render: () =>
    page({
      active: STORIES.photos,
      body: (() => {
        const wrap = document.createElement('div');
        wrap.appendChild(
          head('Photos', 'At least one photo in the required formats is needed for a complete description.', badge('EDITING', 'draft')),
        );
        wrap.appendChild(
          card(
            'Product photos',
            `<div class="om-thumbs">
           <div class="om-thumb filled">kosiarka-01.jpg<br>1200×1200</div>
           <div class="om-thumb filled">kosiarka-02.jpg<br>1200×1200</div>
           <div class="om-thumb add">+ add photo<br>JPG / PNG</div>
           <div class="om-thumb">min. 1000×1000</div>
         </div>
         <p class="om-note">Photos may come from the vendor or be processed before upload. Unpublished until the description is published.</p>`,
          ),
        );
        const actions = document.createElement('div');
        actions.className = 'om-actions';
        actions.appendChild(goto('← Back to draft', STORIES.draft));
        actions.appendChild(goto('Request review →', STORIES.queue, 'primary'));
        wrap.appendChild(actions);
        return wrap;
      })(),
    }),
};

const Queue = {
  name: '05 Review Queue',
  render: () =>
    page({
      active: STORIES.queue,
      body: (() => {
        const wrap = document.createElement('div');
        wrap.appendChild(
          head('Pending reviews', 'Descriptions waiting for a reviewer. The reviewer is never the author.'),
        );
        wrap.appendChild(
          card(
            null,
            table(
              ['Product', 'Author', 'Submitted', 'Waiting', 'Completeness', ''],
              [
                [
                  openDraft,
                  'A. Kowalska',
                  '2019-10-02 09:14',
                  '3 days',
                  badge('2 items missing', 'review'),
                  '<button class="om-btn om-review">Review</button>',
                ],
                [
                  'Miotła ogrodowa 120',
                  'A. Kowalska',
                  '2019-10-03 11:02',
                  '2 days',
                  badge('complete', 'published'),
                  '<button class="om-btn om-review">Review</button>',
                ],
                [
                  'Grill ogrodowy 44',
                  'M. Nowak',
                  '2019-10-04 16:40',
                  '1 day',
                  badge('complete', 'published'),
                  '<button class="om-btn om-review">Review</button>',
                ],
              ],
            ),
          ),
        );
        wrap.querySelectorAll('.om-review').forEach((b) =>
          b.addEventListener('click', () => import('./shell.js').then((m) => m.navigateTo(STORIES.review))),
        );
        return wrap;
      })(),
    }),
};

const Review = {
  name: '06 Review Detail',
  render: () =>
    page({
      active: STORIES.queue,
      body: (() => {
        const wrap = document.createElement('div');
        wrap.appendChild(head('Review — ' + openDraft, 'Author A. Kowalska · submitted 2019-10-02 09:14', badge('PENDING REVIEW', 'review')));
        wrap.appendChild(
          card(
            'Quality gate',
            `<div class="om-banner err"><b>Publication is blocked</b>1 item is missing and the grammar check reports 1 issue.</div>
         <ul class="om-missing">
           <li class="done">Title</li>
           <li class="done">Description</li>
           <li class="done">At least one photo</li>
           <li class="todo">Price valid for a date range</li>
         </ul>
         <div class="om-banner warn"><b>Automatic check (advisory)</b>Sentence 3 looks incomplete: "wysokość cięcia regulowana w 4 stopniach."</div>`,
          ),
        );
        wrap.appendChild(
          card('Preview', `<div class="om-kv">
           <dt>Title</dt><dd>${openDraft}</dd>
           <dt>Description</dt><dd>Solidna kosiarka ręczna… (142 words)</dd>
           <dt>Photos</dt><dd>2</dd>
           <dt>Price</dt><dd>— none —</dd>
         </div>`),
        );
        const actions = document.createElement('div');
        actions.className = 'om-actions';
        actions.appendChild(goto('Reject → back to draft', STORIES.draft));
        actions.appendChild(button('Approve & publish', { variant: 'primary', to: STORIES.publish }));
        wrap.appendChild(actions);
        wrap.appendChild(
          card(null, `<p class="om-hint">Approve is enabled, but publishing still runs the gate: with open items the publish screen stays blocked.</p>`),
        );
        return wrap;
      })(),
    }),
};

const Publish = {
  name: '07 Publish',
  render: () =>
    page({
      active: STORIES.publish,
      body: (() => {
        const wrap = document.createElement('div');
        wrap.appendChild(head('Publish version to offer', 'A published version is snapshotted. It reaches the customer only from its availability date.', badge('APPROVED', 'published')));
        wrap.appendChild(
          card(
            'Publication',
            `<div class="om-col"><label class="om-lbl">Availability starts</label>
           <div class="om-range"><input class="om-in" type="date" value="2019-07-01"><span class="om-hint">leave empty to publish immediately</span></div></div>
         <div class="om-col"><label class="om-lbl">Publish what</label>
           <select class="om-sel"><option>Description v3 (approved)</option><option>Description v2 (previous)</option></select></div>
         <div class="om-banner ok"><b>Quality gate passed</b>All mandatory items are present. No open policy issues.</div>
         <p class="om-note">Publishing does not make the version visible before its availability date — the customer keeps seeing the previous version until then.</p>`,
          ),
        );
        const actions = document.createElement('div');
        actions.className = 'om-actions';
        actions.appendChild(goto('← Review', STORIES.queue));
        actions.appendChild(goto('Publish & schedule', STORIES.catalog, 'primary'));
        wrap.appendChild(actions);
        return wrap;
      })(),
    }),
};

const Pricing = {
  name: '08 Pricing',
  render: () =>
    page({
      active: STORIES.pricing,
      body: (() => {
        const wrap = document.createElement('div');
        wrap.appendChild(head('Prices and discounts', 'Sales defines a price or a discount for a date range. Changes are scheduled, not immediate.', ''));
        wrap.appendChild(
          card(
            null,
            table(
              ['#', 'Kind', 'Amount', 'Valid from', 'Valid to', 'State', ''],
              [
                ['1', 'Price', '249,00 PLN', '2019-06-01', '2019-06-30', badge('EXPIRED', 'removed'), '<button class="om-btn">Edit</button>'],
                ['2', 'Price', '259,00 PLN', '2019-07-01', '—', badge('SCHEDULED', 'scheduled'), '<button class="om-btn">Edit</button>'],
                ['3', 'Discount', '−10%', '2019-07-01', '2019-07-31', badge('SCHEDULED', 'scheduled'), '<button class="om-btn">Edit</button>'],
                ['4', 'Discount', '−15%', '2019-08-01', '2019-08-15', badge('SCHEDULED', 'scheduled'), '<button class="om-btn">Edit</button>'],
              ],
            ),
          ),
        );
        wrap.appendChild(
          card(
            'Add price or discount',
            `<div class="om-row">
           <div class="om-col" style="flex:0 0 130px"><label class="om-lbl">Kind</label><select class="om-sel"><option>Price</option><option>Discount</option></select></div>
           <div class="om-col" style="flex:0 0 150px"><label class="om-lbl">Amount</label><input class="om-in" value="259,00 PLN"></div>
           <div class="om-col" style="flex:0 0 170px"><label class="om-lbl">Valid from</label><input class="om-in" type="date" value="2019-07-01"></div>
           <div class="om-col" style="flex:0 0 170px"><label class="om-lbl">Valid to</label><input class="om-in" type="date"></div>
         </div>
         <p class="om-note">Overlapping ranges and the rounding rule are still open (see the spec registers).</p>`,
          ),
        );
        const actions = document.createElement('div');
        actions.className = 'om-actions';
        actions.appendChild(goto('← Catalog', STORIES.catalog));
        actions.appendChild(goto('Save & schedule', STORIES.catalog, 'primary'));
        wrap.appendChild(actions);
        return wrap;
      })(),
    }),
};

const Versions = {
  name: '09 Versions and Removal',
  render: () =>
    page({
      active: STORIES.versions,
      body: (() => {
        const wrap = document.createElement('div');
        wrap.appendChild(head('Version history — ' + openDraft, 'A snapshot is kept for every published version.', ''));
        wrap.appendChild(
          card(
            null,
            table(
              ['Version', 'Published', 'Availability', 'State', ''],
              [
                ['v3', '—', 'from 2019-07-01', badge('SCHEDULED', 'scheduled'), '<button class="om-btn">View</button>'],
                ['v2', '2019-05-02', '2019-05-02 – 2019-06-30', badge('SUPERSEDED', 'draft'), '<button class="om-btn om-revert">Revert to this</button>'],
                ['v1', '2019-03-11', '2019-03-11 – 2019-05-01', badge('SUPERSEDED', 'draft'), '<button class="om-btn om-revert">Revert to this</button>'],
              ],
            ),
          ),
        );
        wrap.appendChild(
          card(
            'Remove product from offer',
            `<p class="om-hint">Removes the product from the offer. The customer keeps seeing it in an existing basket, marked as no longer available.</p>
         <div class="om-actions"></div>`,
          ),
        );
        const danger = document.createElement('div');
        danger.className = 'om-actions';
        danger.appendChild(goto('← Catalog', STORIES.catalog));
        danger.appendChild(button('Remove from offer', { variant: 'danger', to: STORIES.catalog }));
        wrap.querySelectorAll('.om-card')[1].appendChild(danger);
        wrap.querySelectorAll('.om-revert').forEach((b) =>
          b.addEventListener('click', () => import('./shell.js').then((m) => m.navigateTo(STORIES.draft))),
        );
        return wrap;
      })(),
    }),
};

export { Catalog, Create, Draft, Photos, Queue, Review, Publish, Pricing, Versions };
