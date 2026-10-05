# Materials — offer management

Source materials are stored in `raw/` at the repository root and linked here (no copies,
so the 223 MB video never enters git).

| # | Source | Type | What it is | Use for | Status |
|---|---|---|---|---|---|
| 1 | [`raw/ecommerce-eventstorming.improved.srt`](../../../../raw/ecommerce-eventstorming.improved.srt) | transcript | 153 segments / 32 min — catalog → browsing → basket → checkout → fulfilment | goals, actors, rules, calculations, boundaries | fact — source of record |
| 2 | [`raw/ecommerce-eventstorming.jpg`](../../../../raw/ecommerce-eventstorming.jpg) | diagram | Event-storming board: 5 areas + Warehouse / crm / dhl | actors, commands, events, read models, policies, boundaries | fact |
| 3 | [`raw/ecommerce-eventstorming.hevc.mp4`](../../../../raw/ecommerce-eventstorming.hevc.mp4) | video | Session recording | — | excluded (transcript is the source of record) |
| 4 | [`raw/offer-lego.png`](../../../../raw/offer-lego.png) | screenshot | Allegro offer page, LEGO Star Wars 75423 | inspiration for the scope of description images / properties only — not the design | fact |
| 5 | [`raw/goods-issue-note.png`](../../../../raw/goods-issue-note.png) | data | WZ 1/10/2019 goods issue note | fulfilment document concept (out of this scope) | fact |
| 6 | [`raw/consignment-note.png`](../../../../raw/consignment-note.png) | data | DPD carrier label | carrier adapter artifact (out of this scope) | fact |
| 7 | [`raw/goods-issue-note.json`](../../../../raw/goods-issue-note.json) | data | Derived extraction of #5 | example data | fact (derived) |
| 8 | [`raw/consignment-note.json`](../../../../raw/consignment-note.json) | data | Derived extraction of #6 | example data | fact (derived) |
| 9 | [`docs/arch/microservice-java-spring/`](../../../arch/microservice-java-spring/) | spec | Java 25 / Spring Boot 4.0.6 hexagon blueprint arch docs | architectural constraints for every element | fact |

## Not provided

- No API contracts for any external system (warehouse, CRM, carrier).
- No pricing rules, units or rounding conventions.
- No UI designs for offer management.
- No success-metric baseline.
