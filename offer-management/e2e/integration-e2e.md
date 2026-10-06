# I6 — integration e2e: `offer management` → `browsing offer`

Spec row: `docs/specs/offer-management/spec.md:228` —
**I6** "an event reaches the `browsing offer` consumer and its read model updates; read model reflects the published version".

Contract: `docs/specs/offer-management/03-browsing-offer-integration.md` — exactly three events cross
(`ProductVersionPublishedToOffer`, `ProductPricesChanged`, `ProductRemovedFromOffer`), `_v1` type names,
payloads exactly as the YAML there.

## Verdict

**Partial — 1 of 3 boundary events crosses.** The consumer works and its read model updates, but only
`ProductPricesChanged` is ever published. `ProductVersionPublishedToOffer` and `ProductRemovedFromOffer`
are recorded as *domain* events but are never appended to the outbox, so they never reach Kafka
(see [Finding F1](#f1--the-offer-side-boundary-events-are-never-published)).

| Boundary event | Reaches consumer | Read model effect | Verdict |
|---|---|---|---|
| `ProductPricesChanged` | yes | `price`, `discountPercent` set | PASS |
| `ProductVersionPublishedToOffer` | **no** | `title`/`version`/`present` stay null/false | **FAIL (implementation bug)** |
| `ProductRemovedFromOffer` | **no** | `present` stays false, `lastEvent` unchanged | **FAIL (implementation bug)** |
| 8 private events (`DescriptionUpdated`, `PhotoAddedInRightFormats`, …) | no (by design) | — | PASS (none leaked) |

Runner: `offer-management/e2e/run-integration.sh` → **exit 1** (`2 passed, 3 failed`), the honest result.

## Environment (observed)

- k3s runs as the privileged podman container `offer-management-k3s`; namespace `offer-management`.
- Deployment restarted per the node instruction (`deploy-k3s.sh deploy` then
  `kubectl rollout restart deploy/offer-management`) so the running image is the current tree.
- Endpoints: app `http://localhost:8080` (traefik), Keycloak `http://localhost:18081`,
  business date `2026-10-06` (Europe/Warsaw).
- `kubectl get pods`: `offer-management`, `browsing-offer-consumer`, `kafka`,
  `offer-management-db-postgresql-0`, `keycloak` — all `Running`.

## Consumer

`offer-management/k8s/infra/browsing-offer-consumer.yaml` — ConfigMap (bash consumer) + Deployment + Service.
The `apache/kafka:4.0.0` image already in the containerd is reused (no new image import into the
disk-tight podman VM). The consumer:

1. subscribes to the single topic `browsing-offer` (`kafka-console-consumer.sh --from-beginning`);
2. materialises a per-`productId` read model (title, version, availableFrom, price, discountPercent,
   present, lastEvent) under `/www`;
3. serves it over HTTP via a single-shot `nc` responder (the image's busybox has no `httpd` applet).

It never writes back — the boundary is outbound-only.

## Steps and evidence

| # | Step | Command (abbreviated) | Observed result | Verdict |
|---|---|---|---|---|
| 1 | Deploy consumer | `kubectl apply --validate=false -f k8s/infra/browsing-offer-consumer.yaml` | `deployment "browsing-offer-consumer" successfully rolled out` (1/1 Ready) | PASS |
| 2 | Query read model over HTTP | `kubectl -n offer-management port-forward svc/browsing-offer-consumer 18090:8080` then `curl -s localhost:18090/` | HTTP 200, NDJSON per product (see §Raw output) | PASS |
| 3 | Create product (carla) | `POST /products` | `201`, `productId=p-8757cdd9-…`, `state=DRAFT`, `descriptionVersion=v1` | PASS |
| 4 | Save draft (carla) | `PUT /products/{id}/description-draft` | `200` | PASS |
| 5 | Add photo (carla) | `POST /products/{id}/description-draft/photos` | `201` | PASS |
| 6 | Request review (carla) | `POST /products/{id}/description-draft/review-requests` | `201`, `rr-47ec4601-…` | PASS |
| 7 | Approve as a different person (marta) | `POST /review-requests/{rr}/approval` | `200` | PASS |
| 8 | Schedule PRICE 259.00 PLN + DISCOUNT 10% effective today (sara) | `POST /products/{id}/prices` | `201` (sch. price gates the publish) | PASS |
| 9 | Publish (carla) | `POST /products/{id}/publications` | `201`, `state=PUBLISHED`, `visibleFrom=2026-10-06` | PASS (API) |
| 10 | Prove `ProductVersionPublishedToOffer` reaches consumer | consumer read model | **not received**; `title/version` stay `null`, `present=false` | **FAIL** |
| 11 | Trigger price activation (startup sweep) | `kubectl rollout restart deploy/offer-management` | `ProductPricesChanged` published + consumed | PASS |
| 12 | Prove `ProductPricesChanged` reaches consumer | consumer read model | `price={"value":"259.00","currency":"PLN"}`, `discountPercent="10"` | PASS |
| 13 | Remove from offer (carla) | `DELETE /products/{id}/offer-presence` | `204` | PASS (API) |
| 14 | Prove `ProductRemovedFromOffer` reaches consumer | consumer read model | **not received**; `lastEvent` still `ProductPricesChanged_v1` | **FAIL** |
| 15 | Prove no private event leaked | topic dump + `outbox` table | topic carries only `ProductPricesChanged_v1` | PASS |

`ProductPricesChanged` is emitted by `PriceLifecycleScheduler` on the `SCHEDULED -> ACTIVE` sweep
(`price-lifecycle` node); the runner schedules the price effective *today* and restarts the app to run
the startup sweep (the `@Scheduled` cadence is `PT1H`, `PriceLifecycleScheduler.java:31`).

## Raw output

### Runner

```
[i6] business date (Europe/Warsaw) = 2026-10-06
[i6] created product p-8757cdd9-9d26-41b4-9da5-d1afc24c6ff3
[i6] current draft version v1
[i6] review rr-47ec4601-ce1f-4434-a41f-0b9b42f9452e approved by marta
[i6] scheduled PRICE 259.00 PLN and DISCOUNT 10% effective 2026-10-06
[i6] publish response: {"publicationId":"pub-b63217c1-...","productId":"p-8757cdd9-...","descriptionVersion":"v1","state":"PUBLISHED","visibleFrom":"2026-10-06","publishedAt":"2026-10-06T13:20:46.133929042Z"}
[i6] FAIL  ProductVersionPublishedToOffer did not reach the consumer
[i6] restarting offer-management to run the startup price sweep (PT1H cadence otherwise)
  read model after price activation: {"productId": "p-8757cdd9-...", "title": null, "version": null, "availableFrom": null, "price": {"value": "259.00", "currency": "PLN"}, "discountPercent": "10", "present": false, "lastEvent": "ProductPricesChanged_v1", "events": 4}
[i6] PASS  ProductPricesChanged reached the consumer; ...
[i6] FAIL  ProductRemovedFromOffer did not reach the consumer
  topic event types: ProductPricesChanged_v1
[i6] PASS  no private event crossed the boundary (topic types are a subset of the three)
[i6] FAIL  boundary event types never published to browsing-offer: ProductRemovedFromOffer_v1, ProductVersionPublishedToOffer_v1

[i6] 2 passed, 3 failed
EXIT=1
```

### Read model over HTTP (`curl http://localhost:18090/`)

```
{  "productId": "p-209f6dde-61fd-41f6-a50c-8b6c9d06a05a",  "title": null,  "version": null,  "availableFrom": null,  "price": {"value":"259.00","currency":"PLN"},  "discountPercent": "10",  "present": false,  "lastEvent": "ProductPricesChanged_v1",  "events": 2}
{  "productId": "p-8757cdd9-9d26-41b4-9da5-d1afc24c6ff3",  "title": null,  "version": null,  "availableFrom": null,  "price": {"value":"259.00","currency":"PLN"},  "discountPercent": "10",  "present": false,  "lastEvent": "ProductPricesChanged_v1",  "events": 4}
{  "productId": "p-cc1b812e-d3e0-4c38-ad8f-b3f969ed03f8",  "title": null,  "version": null,  "availableFrom": null,  "price": {"value":"259.00","currency":"PLN"},  "discountPercent": "10",  "present": false,  "lastEvent": "ProductPricesChanged_v1",  "events": 2}
[http 200]
```

`title`, `version` and `present` are `null`/`false` because the publish event never arrived.

### Consumer log (`kubectl logs deploy/browsing-offer-consumer`)

```
2026-10-06T13:21:58Z consumer: consumed ProductPricesChanged_v1 product=p-8757cdd9-9d26-41b4-9da5-d1afc24c6ff3
2026-10-06T13:21:58Z consumer: consumed ProductPricesChanged_v1 product=p-8757cdd9-9d26-41b4-9da5-d1afc24c6ff3
```

### Topic `browsing-offer` (key|value)

```
p-209f6dde-...|{"@type": "ProductPricesChanged_v1", "audit": {"at": "2026-10-06T13:17:02.995790987Z", "who": {"subject": "system"}}, "price": {"value": "259.00", "currency": "PLN"}, "productId": "p-209f6dde-...", "effectiveFrom": "2026-10-06", "discountPercent": "10"}
```

Only `ProductPricesChanged_v1` is present; the partition key is the `productId`.

### Domain events vs what crossed (PostgreSQL)

Private draft events — recorded in the event store, never on the topic:

```
 draft_events: BlankDraftCreated_v1, DescriptionUpdated_v1, PhotoAddedInRightFormats_v1,
               DescriptionPendingReview_v1, DescriptionReviewApproved_v1
```

Offer domain events — recorded in the event store, never appended to the outbox (all three offer-side
domain types, `DescriptionReverted_v1` included; the last is private until published, cf. element 03):

```
 product_events: ProductVersionPublishedToOffer_v1 (13), ProductRemovedFromOffer_v1 (10),
                 DescriptionReverted_v1 (8)
```

The outbox — the only thing that reaches Kafka:

```
 event_type              | count
-------------------------+-------
 ProductPricesChanged_v1 |     6
```

This is the proof of both directions: private events do **not** cross (correct), and the two
non-price boundary events do **not** cross (bug).

## F1 — the offer-side boundary events are never published

**Symptom.** After a `201 PUBLISHED` and a `204` offer removal, `product_events` holds
`ProductVersionPublishedToOffer_v1` / `ProductRemovedFromOffer_v1`, but the `outbox` table holds no
matching row and topic `browsing-offer` receives nothing.

**Cause.** The integration layer for the two offer-side events does not exist. The only production
call to `Outbox.append` is in the price scheduler:

- `offer-management/src/main/java/com/example/offer/mediators/OfferLifecycleMediator.java:128` — `publish(...)` never appends `ProductVersionPublishedToOffer`;
- `offer-management/src/main/java/com/example/offer/mediators/OfferLifecycleMediator.java:162` — `removeFromOffer(...)` never appends `ProductRemovedFromOffer`;
- `offer-management/src/main/java/com/example/offer/pricing/PriceLifecycleScheduler.java:43` — the only `outbox.append(...)` in main code.

The offer aggregate emits the *domain* events (`offer/Product.java:60`, `offer/Product.java:88`) and
`ProductDocumentWithHistoryRepository.save` persists them to `product_events` and republishes them as
in-process Spring events, but no listener translates them into `IntegrationEvent`s. Contrast the
pricing context, which appends `ProductPricesChanged` directly in its own scheduler.

**To close.** Wire the two offer-side integration events into the transactional outbox, in the same
transaction as the state change (as pricing does): build `IntegrationEvent.ProductVersionPublishedToOffer`
from the frozen `DescriptionVersion` + the draft photos at publish time, and
`IntegrationEvent.ProductRemovedFromOffer(productId, audit)` at removal. That is an implementation
change spanning the mediator and the publishing context's declared exposed surface
(`ArchitectureOfPublishingContextTest.sharedKernelExposed`), so it belongs to its own node rather than
to this verification node. Do **not** relax the contract or the architecture tests to make I6 green.

## Reproduce

```
bash offer-management/e2e/run-integration.sh          # exits 1 while F1 is open
KUBECONFIG=offer-management/.k3s-kubeconfig kubectl -n offer-management logs deploy/browsing-offer-consumer
```
