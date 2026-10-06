# I6 — integration e2e: `offer management` → `browsing offer`

Spec row: `docs/specs/offer-management/spec.md:228` —
**I6** "an event reaches the `browsing offer` consumer and its read model updates; read model reflects the published version".

Contract: `docs/specs/offer-management/03-browsing-offer-integration.md` — exactly three events cross
(`ProductVersionPublishedToOffer`, `ProductPricesChanged`, `ProductRemovedFromOffer`), `_v1` type names,
payloads exactly as the YAML there.

## Verdict

**Pass — all three boundary events cross and the read model updates.** Repair of
[F1](#f1--the-offer-side-boundary-events-were-never-published): the two offer-side integration events
are now appended to the outbox in the same transaction as the state change, so all three element-03
events reach Kafka and the consumer.

| Boundary event | Reaches consumer | Read model effect | Verdict |
|---|---|---|---|
| `ProductVersionPublishedToOffer` | yes | `title`, `version`, `present=true` set | PASS |
| `ProductPricesChanged` | yes | `price`, `discountPercent` set | PASS |
| `ProductRemovedFromOffer` | yes | `present=false` | PASS |
| 8 private events (`DescriptionUpdated`, `PhotoAddedInRightFormats`, …) | no (by design) | — | PASS (none leaked) |

Runner: `offer-management/e2e/run-integration.sh` → **exit 0** (`5 passed, 0 failed`).

## Environment (observed)

- k3s runs as the privileged podman container `offer-management-k3s`; namespace `offer-management`.
- Deployment restarted per the node instruction (`deploy-k3s.sh deploy` then
  `kubectl rollout restart deploy/offer-management`) so the running image is the current tree.
- Endpoints: app `http://localhost:8080` (traefik), Keycloak `http://localhost:18081`,
  business date `2026-10-06` (Europe/Warsaw), probe product
  `p-e0c2de2f-5809-457b-b35f-3590fcf711d6`.
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
| 3 | Create product (carla) | `POST /products` | `201`, `productId=p-e0c2de2f-…`, `state=DRAFT`, `descriptionVersion=v1` | PASS |
| 4 | Save draft (carla) | `PUT /products/{id}/description-draft` | `200` | PASS |
| 5 | Add photo (carla) | `POST /products/{id}/description-draft/photos` | `201` | PASS |
| 6 | Request review (carla) | `POST /products/{id}/description-draft/review-requests` | `201`, `rr-694031fd-…` | PASS |
| 7 | Approve as a different person (marta) | `POST /review-requests/{rr}/approval` | `200` | PASS |
| 8 | Schedule PRICE 259.00 PLN + DISCOUNT 10% effective today (sara) | `POST /products/{id}/prices` | `201` (sch. price gates the publish) | PASS |
| 9 | Publish (carla) | `POST /products/{id}/publications` | `201`, `state=PUBLISHED`, `visibleFrom=2026-10-06` | PASS (API) |
| 10 | Prove `ProductVersionPublishedToOffer` reaches consumer | consumer read model after publish | `title="Kosiarka ręczna 340 (I6)"`, `version="v1"`, `present=true`, `lastEvent=ProductVersionPublishedToOffer_v1` | PASS |
| 11 | Trigger price activation (startup sweep) | `kubectl rollout restart deploy/offer-management` | `ProductPricesChanged` published + consumed | PASS |
| 12 | Prove `ProductPricesChanged` reaches consumer | consumer read model after activation | `price={"value":"259.00","currency":"PLN"}`, `discountPercent="10"`, `present=true` | PASS |
| 13 | Remove from offer (carla) | `DELETE /products/{id}/offer-presence` | `204` | PASS (API) |
| 14 | Prove `ProductRemovedFromOffer` reaches consumer | consumer read model after removal | `present=false`, `lastEvent=ProductRemovedFromOffer_v1` | PASS |
| 15 | Prove no private event leaked | topic dump + `outbox` table | topic carries only the three element-03 types | PASS |
| 16 | Prove all three boundary types were published | topic dump filtered by product | `ProductPricesChanged_v1`, `ProductRemovedFromOffer_v1`, `ProductVersionPublishedToOffer_v1` | PASS |

`ProductPricesChanged` is emitted by `PriceLifecycleScheduler` on the `SCHEDULED -> ACTIVE` sweep
(`price-lifecycle` node); the runner schedules the price effective *today* and restarts the app to run
the startup sweep (the `@Scheduled` cadence is `PT1H`, `PriceLifecycleScheduler.java:31`).

## Raw output

### Runner

```
[i6] business date (Europe/Warsaw) = 2026-10-06
[i6] created product p-e0c2de2f-5809-457b-b35f-3590fcf711d6
[i6] current draft version v1
[i6] review rr-694031fd-c939-479f-9edc-9cdb32e1fda4 approved by marta
[i6] scheduled PRICE 259.00 PLN and DISCOUNT 10% effective 2026-10-06
[i6] publish response: {"publicationId":"pub-b381652c-aacf-4816-9720-91f03d685107","productId":"p-e0c2de2f-...","descriptionVersion":"v1","state":"PUBLISHED","visibleFrom":"2026-10-06","publishedAt":"2026-10-06T13:30:39.704433486Z"}
  read model after publish: {"productId": "p-e0c2de2f-...", "title": "Kosiarka ręczna 340 (I6)", "version": "v1", "availableFrom": null, "price": null, "discountPercent": null, "present": true, "lastEvent": "ProductVersionPublishedToOffer_v1", "events": 1}
[i6] PASS  ProductVersionPublishedToOffer reached the consumer; present=true, version=v1
[i6] restarting offer-management to run the startup price sweep (PT1H cadence otherwise)
  read model after price activation: {"productId": "p-e0c2de2f-...", "title": "Kosiarka ręczna 340 (I6)", "version": "v1", "availableFrom": null, "price": {"value": "259.00", "currency": "PLN"}, "discountPercent": "10", "present": true, "lastEvent": "ProductPricesChanged_v1", "events": 5}
[i6] PASS  ProductPricesChanged reached the consumer; ...
  read model after removal: {"productId": "p-e0c2de2f-...", "title": "Kosiarka ręczna 340 (I6)", "version": "v1", "availableFrom": null, "price": {"value": "259.00", "currency": "PLN"}, "discountPercent": "10", "present": false, "lastEvent": "ProductRemovedFromOffer_v1", "events": 6}
[i6] PASS  ProductRemovedFromOffer reached the consumer; present=false
  topic event types: ProductPricesChanged_v1, ProductRemovedFromOffer_v1, ProductVersionPublishedToOffer_v1
[i6] PASS  no private event crossed the boundary (topic types are a subset of the three)
[i6] PASS  all three boundary event types were observed on topic browsing-offer

[i6] 5 passed, 0 failed
EXIT=0
```

### Read model over HTTP (`curl http://localhost:18090/`) — before / after

Before any publish event for this product the read model has **no row** (creation, the draft edits, the
photo and the review are private). After each boundary event:

```
# after ProductVersionPublishedToOffer
{  "productId": "p-e0c2de2f-...",  "title": "Kosiarka ręczna 340 (I6)",  "version": "v1",  "availableFrom": null,  "price": null,  "discountPercent": null,  "present": true,  "lastEvent": "ProductVersionPublishedToOffer_v1",  "events": 1}

# after ProductPricesChanged
{  "productId": "p-e0c2de2f-...",  "title": "Kosiarka ręczna 340 (I6)",  "version": "v1",  "availableFrom": null,  "price": {"value":"259.00","currency":"PLN"},  "discountPercent": "10",  "present": true,  "lastEvent": "ProductPricesChanged_v1",  "events": 5}

# after ProductRemovedFromOffer
{  "productId": "p-e0c2de2f-...",  "title": "Kosiarka ręczna 340 (I6)",  "version": "v1",  "availableFrom": null,  "price": {"value":"259.00","currency":"PLN"},  "discountPercent": "10",  "present": false,  "lastEvent": "ProductRemovedFromOffer_v1",  "events": 6}
```

### Consumer log (`kubectl logs deploy/browsing-offer-consumer`)

```
2026-10-06T13:30:43Z consumer: consumed ProductVersionPublishedToOffer_v1 product=p-e0c2de2f-5809-457b-b35f-3590fcf711d6
2026-10-06T13:31:08Z consumer: consumed ProductPricesChanged_v1 product=p-e0c2de2f-5809-457b-b35f-3590fcf711d6
2026-10-06T13:31:08Z consumer: consumed ProductPricesChanged_v1 product=p-e0c2de2f-5809-457b-b35f-3590fcf711d6
2026-10-06T13:31:09Z consumer: consumed ProductPricesChanged_v1 product=p-e0c2de2f-5809-457b-b35f-3590fcf711d6
2026-10-06T13:31:09Z consumer: consumed ProductPricesChanged_v1 product=p-e0c2de2f-5809-457b-b35f-3590fcf711d6
2026-10-06T13:31:14Z consumer: consumed ProductRemovedFromOffer_v1 product=p-e0c2de2f-5809-457b-b35f-3590fcf711d6
```

### Topic `browsing-offer` (key|value) for the probe product — complete

```
p-e0c2de2f-...|{"@type": "ProductVersionPublishedToOffer_v1", "audit": {"at": "2026-10-06T13:30:39.704433486Z", "who": {"subject": "aa109af8-..."}}, "title": "Kosiarka ręczna 340 (I6)", "photos": [{"mime": "image/jpeg", "width": 1200, "height": 1200, "photoId": "ph-7f8d5aa1-..."}], "version": "v1", "productId": "p-e0c2de2f-...", "attributes": {}, "description": "Solidna kosiarka ręczna do trawy i chwastów.", "availableFrom": null}
p-e0c2de2f-...|{"@type": "ProductPricesChanged_v1", "audit": {"at": "2026-10-06T13:31:06.824021581Z", "who": {"subject": "system"}}, "price": {"value": "259.00", "currency": "PLN"}, "productId": "p-e0c2de2f-...", "effectiveFrom": "2026-10-06", "discountPercent": "10"}
p-e0c2de2f-...|{"@type": "ProductPricesChanged_v1", "audit": {"at": "2026-10-06T13:31:06.824021581Z", "who": {"subject": "system"}}, "price": {"value": "259.00", "currency": "PLN"}, "productId": "p-e0c2de2f-...", "effectiveFrom": "2026-10-06", "discountPercent": "10"}
p-e0c2de2f-...|{"@type": "ProductPricesChanged_v1", "audit": {"at": "2026-10-06T13:31:06.824021581Z", "who": {"subject": "system"}}, "price": {"value": "259.00", "currency": "PLN"}, "productId": "p-e0c2de2f-...", "effectiveFrom": "2026-10-06", "discountPercent": "10"}
p-e0c2de2f-...|{"@type": "ProductPricesChanged_v1", "audit": {"at": "2026-10-06T13:31:06.824021581Z", "who": {"subject": "system"}}, "price": {"value": "259.00", "currency": "PLN"}, "productId": "p-e0c2de2f-...", "effectiveFrom": "2026-10-06", "discountPercent": "10"}
p-e0c2de2f-...|{"@type": "ProductRemovedFromOffer_v1", "audit": {"at": "2026-10-06T13:31:13.305405188Z", "who": {"subject": "aa109af8-..."}}, "productId": "p-e0c2de2f-..."}
```

All three `_v1` type names and payloads match element 03; the partition key is the `productId`. The
four `ProductPricesChanged_v1` lines are the at-least-once replay of the same idempotency key
`(productId, effectiveFrom)` — the consumer dedupes the read-model effect but each delivered record is
shown, matching the consumer log and the read model's `events: 5` (1 publish + 4 price deliveries +
1 removal = 6 at the end).

Two disclosed deviations from the literal YAML example, both shared with the pre-existing
`ProductPricesChanged` path:

- `audit.who` is an object (`{"subject": "…"}`), not the YAML's username scalar — the token carries the
  subject.
- `attributes` omits `manualUrl` when it is null (absent key rather than explicit `null`). The mediator
  builds the map once for the frozen `DescriptionVersion`, whose `Map.copyOf` forbids null values
  (`DescriptionVersion.java:24`); consumers treat absent and `null` alike. The record itself preserves
  an explicit `manualUrl: null` when constructed directly — covered by
  `OutboxRelayTest.payloadsCarryExactlyTheElement03Fields`.

### Domain events vs what crossed (PostgreSQL)

Private draft events — recorded in the event store, never on the topic:

```
 draft_events: BlankDraftCreated_v1 (40), DescriptionUpdated_v1 (25), PhotoAddedInRightFormats_v1 (25),
               DescriptionPendingReview_v1 (19), DescriptionReviewApproved_v1 (15),
               DescriptionReviewRejected_v1 (2), PhotoRemoved_v1 (5)
```

Offer domain events — recorded in the event store; the three boundary ones are now also appended to the
outbox (`DescriptionReverted_v1` stays private until published, cf. element 03):

```
 product_events: ProductVersionPublishedToOffer_v1 (14), ProductRemovedFromOffer_v1 (11),
                 DescriptionReverted_v1 (8)
```

The outbox — the only thing that reaches Kafka:

```
 event_type              | count
-------------------------+-------
 ProductPricesChanged_v1 |     8
 ProductRemovedFromOffer_v1        |     1
 ProductVersionPublishedToOffer_v1 |     1
```

Outbox rows for the probe product (payloads as element 03, with the two disclosed deviations above):

```
ProductVersionPublishedToOffer_v1 | {"@type": "ProductVersionPublishedToOffer_v1", "audit": {"at": "2026-10-06T13:30:39.704433486Z", "who": {"subject": "aa109af8-..."}}, "title": "Kosiarka ręczna 340 (I6)", "photos": [{"mime": "image/jpeg", "width": 1200, "height": 1200, "photoId": "ph-7f8d5aa1-..."}], "version": "v1", "productId": "p-e0c2de2f-...", "attributes": {}, "description": "Solidna kosiarka ręczna do trawy i chwastów.", "availableFrom": null}
ProductPricesChanged_v1           | {"@type": "ProductPricesChanged_v1", ... "price": {"value": "259.00", "currency": "PLN"}, "effectiveFrom": "2026-10-06", "discountPercent": "10"}
ProductRemovedFromOffer_v1        | {"@type": "ProductRemovedFromOffer_v1", "audit": {"at": "2026-10-06T13:31:13.305405188Z", "who": {"subject": "aa109af8-..."}}, "productId": "p-e0c2de2f-..."}
```

Private events do **not** cross and all three boundary events do.

## F1 — the offer-side boundary events were never published

**Symptom (before the fix).** After a `201 PUBLISHED` and a `204` offer removal, `product_events` held
`ProductVersionPublishedToOffer_v1` / `ProductRemovedFromOffer_v1`, but the `outbox` table held no
matching row and topic `browsing-offer` received nothing.

**Cause.** The integration layer for the two offer-side events was missing. The only production caller
of `Outbox.append` was the price scheduler; the mediator's `publish(...)` and `removeFromOffer(...)`
changed state but appended nothing.

**Fix.** `OfferLifecycleMediator` now appends the two integration events in the same transaction as the
state change, exactly as the pricing context does:

- `publish(...)` → `outbox.append(new ProductVersionPublishedToOffer(productId, version.version(),
  availableFrom, version.title(), version.description(), version.attributes(), photoViews(version, draft),
  audit))` — the resolved view element 03 requires (`title`, `description`, `attributes`, `photos`), the
  photo metadata joined from the draft snapshot (element 03, "the draft is private, so the shop cannot
  fetch them");
- `removeFromOffer(...)` → `outbox.append(new ProductRemovedFromOffer(productId, audit))`.

The mediator stays an orchestrator — it makes no business decision and adds no invariant; the payload is
plain mapping of already-decided state. The two event records were added to
`ArchitectureOfPublishingContextTest.sharedKernelExposed` (the declared exposed surface), no ArchUnit
rule was weakened.

## Reproduce

```
bash offer-management/e2e/run-integration.sh          # exits 0 when I6 is green
KUBECONFIG=offer-management/.k3s-kubeconfig kubectl -n offer-management logs deploy/browsing-offer-consumer
```
