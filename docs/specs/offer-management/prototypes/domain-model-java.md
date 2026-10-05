# Java model — draft editor and effective price

Proposal following the blueprint's `domain-model.md`, `policy.md` and `code-structure.md`.
Package root is the blueprint's `micro_name` (shown as `com.example.offer`).

```
com.example.offer/
    tools/                        # shared kernel
        Identity.java  Audit.java
    draft/                        # bounded context: draft editor + its review
        DescriptionDraft.java         # aggregate (package-private)
        DraftSnapshot.java  UpdateDraft.java  DomainEvent.java
        Title  Description  DraftAttributes  Photo  DraftState  ReviewRequest
        PhotoFormatPolicy.java    # package-private policy record
        DraftService.java  DraftController.java  DraftRepository.java
    pricing/
        Price  Discount  Money  Percent  DateRange  PriceState
        EffectivePrice.java       # derived
        PriceSchedule.java        # aggregate: overlap + editability rules
    offer/                        # versions + publications
```

---

## 1. Shared kernel

```java
package com.example.offer.tools;

/** Authenticated subject, built in the adapter from the JWT — never inside the domain. */
public record Identity(String subject) {
    public Identity {
        if (subject == null || subject.isBlank()) throw new IllegalArgumentException("identity is required");
    }
}

/** Who changed the state and when. Attached to every state-modifying operation. */
public record Audit(Identity who, Instant at) {
    public Audit {
        Objects.requireNonNull(who);
        Objects.requireNonNull(at);
    }
}
```

Adding a shared-kernel type requires the `ArchitectureDescription` predicates and
`arch-unit.md` context tests to be updated in the same change.

---

## 2. `draft` context

### Events

```java
package com.example.offer.draft;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
        @JsonSubTypes.Type(value = DomainEvent.BlankDraftCreated.class,         name = "BlankDraftCreated_v1"),
        @JsonSubTypes.Type(value = DomainEvent.DescriptionUpdated.class,        name = "DescriptionUpdated_v1"),
        @JsonSubTypes.Type(value = DomainEvent.PhotoAddedInRightFormats.class,  name = "PhotoAddedInRightFormats_v1"),
        @JsonSubTypes.Type(value = DomainEvent.PhotoRemoved.class,              name = "PhotoRemoved_v1"),
        @JsonSubTypes.Type(value = DomainEvent.DescriptionPendingReview.class,  name = "DescriptionPendingReview_v1"),
        @JsonSubTypes.Type(value = DomainEvent.DescriptionReviewApproved.class, name = "DescriptionReviewApproved_v1"),
        @JsonSubTypes.Type(value = DomainEvent.DescriptionReviewRejected.class, name = "DescriptionReviewRejected_v1")
})
public interface DomainEvent {

    /** Opening state — everything that exists at creation, stated explicitly. */
    record BlankDraftCreated(String productId, String version, int revision, DraftState state,
                             Title title, Description description, DraftAttributes attributes,
                             List<Photo> photos, String basedOnVersion,
                             ReviewRequest review, Audit audit) implements DomainEvent {}

    /** The new value of the edited triple — full replacement, not a diff. */
    record DescriptionUpdated(String productId, int revision, Title title,
                              Description description, DraftAttributes attributes,
                              Audit audit) implements DomainEvent {}

    record PhotoAddedInRightFormats(String productId, Photo photo, Audit audit) implements DomainEvent {}

    record PhotoRemoved(String productId, String photoId, Audit audit) implements DomainEvent {}

    record DescriptionPendingReview(String productId, ReviewRequest review, Audit audit) implements DomainEvent {}

    record DescriptionReviewApproved(String productId, ReviewRequest review, Audit audit) implements DomainEvent {}

    record DescriptionReviewRejected(String productId, ReviewRequest review, String reason, Audit audit) implements DomainEvent {}
}
```

### Value objects

```java
public record Title(String value) {
    public Title {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("title is required");   // RULE-6
    }
}

public record Description(String value) {
    public Description {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("description is required");
    }
}

@Builder(toBuilder = true)
public record DraftAttributes(String category, String manualUrl) {
    static DraftAttributes empty() { return new DraftAttributes(null, null); }
}

public record Photo(String photoId, String fileName, String mime,
                    int width, int height, long sizeBytes, int position, Instant uploadedAt) {}

public enum DraftState { EDITING, IN_REVIEW, APPROVED }

public record ReviewRequest(String reviewRequestId, Identity author, Identity decidedBy, Instant at) {

    static ReviewRequest requested(String reviewRequestId, Audit audit) {
        return new ReviewRequest(reviewRequestId, audit.who(), null, audit.at());
    }

    static ReviewRequest decided(ReviewRequest request, Identity decidedBy, Instant at) {
        return new ReviewRequest(request.reviewRequestId(), request.author(), decidedBy, at);
    }

    @JsonIgnore public boolean isPending() { return decidedBy == null; }
    @JsonIgnore public boolean isDecided() { return decidedBy != null; }
}

public record DraftSnapshot(String productId, String version, DraftState state, int revision,
                            Title title, Description description, DraftAttributes attributes,
                            List<Photo> photos, String basedOnVersion, ReviewRequest review,
                            Audit lastChange) {}
```

### Policy

```java
record PhotoFormatPolicy(List<PhotoFormat> allowed) {

    void check(Photo photo) {                                                    // RULE-16, Q21
        PhotoFormat format = allowed.stream()
                .filter(f -> f.mime().equals(photo.mime())).findFirst()
                .orElseThrow(() -> new PhotoFormatUnsupported(photo.mime()));
        if (photo.width() < format.minWidth() || photo.height() < format.minHeight())
            throw new PhotoTooSmall(photo.width(), photo.height());
        if (photo.sizeBytes() > format.maxBytes()) throw new PhotoTooLarge(photo.sizeBytes());
    }
}

public record PhotoFormat(String mime, List<String> extensions, int minWidth, int minHeight, long maxBytes) {}
```

### Aggregate

```java
@AllArgsConstructor
class DescriptionDraft {

    private final String productId;
    private final String version;
    private final List<DomainEvent> events;

    private DraftState state;
    private int revision;
    private Title title;
    private Description description;
    private DraftAttributes attributes;
    private List<Photo> photos;
    private String basedOnVersion;
    private ReviewRequest review;
    private Audit lastChange;

    static DescriptionDraft newDraft(String productId, String version, Title title, Audit audit) {
        Objects.requireNonNull(title);
        Objects.requireNonNull(audit);
        DescriptionDraft draft = new DescriptionDraft(productId, version, new ArrayList<>(),
                DraftState.EDITING, 1, title, null, DraftAttributes.empty(),
                List.of(), null, null, audit);
        draft.events.add(new DomainEvent.BlankDraftCreated(productId, version, draft.revision,
                draft.state, draft.title, draft.description, draft.attributes,
                List.copyOf(draft.photos), draft.basedOnVersion, draft.review, audit));
        return draft;
    }

    void edit(UpdateDraft update, Audit audit) {
        checkEditable();                                                                     // RULE-5
        Title newTitle = firstNonNull(update.title(), title);
        Description newDescription = firstNonNull(update.description(), description);
        DraftAttributes newAttributes = firstNonNull(update.attributes(), attributes);
        if (newTitle.equals(title) && newDescription.equals(description)
                && newAttributes.equals(attributes)) {
            return;                                                                          // RULE-61
        }
        this.title = newTitle;
        this.description = newDescription;
        this.attributes = newAttributes;
        this.revision++;
        this.lastChange = audit;
        events.add(new DomainEvent.DescriptionUpdated(productId, revision, newTitle,
                newDescription, newAttributes, audit));
    }

    void attachPhoto(Photo photo, Audit audit) {
        checkEditable();
        Photo positioned = photo.toBuilder().position(photos.size()).build();                 // RULE-15
        this.photos = List.copyOf(Stream.concat(photos.stream(), Stream.of(positioned)).toList());
        this.lastChange = audit;
        events.add(new DomainEvent.PhotoAddedInRightFormats(productId, positioned, audit));
    }

    void detachPhoto(String photoId, Audit audit) {
        checkEditable();
        List<Photo> remaining = photos.stream().filter(p -> !p.photoId().equals(photoId)).toList();
        if (remaining.size() == photos.size()) return;                                        // RULE-61
        this.photos = renumber(remaining);                                                    // RULE-15
        this.lastChange = audit;
        events.add(new DomainEvent.PhotoRemoved(productId, photoId, audit));
    }

    void requestReview(ReviewRequest request, Audit audit) {
        checkEditable();                                                                      // D3/V3, V4
        checkSamePerson(request.author(), audit.who());                                       // RULE-60
        this.state = DraftState.IN_REVIEW;                                                    // D3/V2 — missing items allowed
        this.review = request;
        this.lastChange = audit;
        events.add(new DomainEvent.DescriptionPendingReview(productId, request, audit));
    }

    void approve(ReviewRequest decision, Audit audit) {
        checkPending(decision.reviewRequestId());                                             // RULE-19
        checkSamePerson(decision.decidedBy(), audit.who());                                   // RULE-60
        checkDifferentPerson(decision.author(), decision.decidedBy());                        // RULE-18 / D5
        this.state = DraftState.APPROVED;                                                     // RULE-20
        this.review = decision;
        this.lastChange = audit;
        events.add(new DomainEvent.DescriptionReviewApproved(productId, decision, audit));
    }

    void reject(ReviewRequest decision, String reason, Audit audit) {
        checkPending(decision.reviewRequestId());
        checkSamePerson(decision.decidedBy(), audit.who());
        checkDifferentPerson(decision.author(), decision.decidedBy());
        this.state = DraftState.EDITING;                                                      // RULE-21
        this.review = decision;
        this.lastChange = audit;
        events.add(new DomainEvent.DescriptionReviewRejected(productId, decision, reason, audit));
    }

    private void checkEditable() {
        if (state != DraftState.EDITING) throw new DraftNotEditable(productId, state);
    }

    private void checkPending(String reviewRequestId) {
        if (review == null || review.isDecided() || !review.reviewRequestId().equals(reviewRequestId))
            throw new ReviewNotPending(reviewRequestId);
    }

    private void checkDifferentPerson(Identity author, Identity decidedBy) {
        if (Objects.equals(author, decidedBy)) throw new ReviewerIsAuthor(productId, author);
    }

    private void checkSamePerson(Identity expected, Identity actual) {
        if (!Objects.equals(expected, actual)) throw new ActorMismatch(expected, actual);
    }

    /** Read model only — never used to build an event. */
    DraftSnapshot toDraftSnapshot() {
        return new DraftSnapshot(productId, version, state, revision, title, description,
                attributes, List.copyOf(photos), basedOnVersion, review, lastChange);
    }
}
```

### Command DTO

```java
@Builder
public record UpdateDraft(@Valid Title title, @Valid Description description,
                          @Valid DraftAttributes attributes) {

    public void apply(DescriptionDraft draft, Audit audit) { draft.edit(this, audit); }
}
```

```java
public record Completeness(boolean complete, List<MissingRequirement> missing) {
    public record MissingRequirement(String code, String label) {}
}
```

`Completeness` is computed by the service from the requirement catalogue and the pricing
context, then attached to the read DTO — the `draft` context never imports `Price`.

---

### Events of the `offer` context

```java
package com.example.offer.offer;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
        @JsonSubTypes.Type(value = DomainEvent.ProductVersionPublishedToOffer.class, name = "ProductVersionPublishedToOffer_v1"),
        @JsonSubTypes.Type(value = DomainEvent.DescriptionReverted.class,            name = "DescriptionReverted_v1"),
        @JsonSubTypes.Type(value = DomainEvent.ProductRemovedFromOffer.class,        name = "ProductRemovedFromOffer_v1")
})
public interface DomainEvent {
    record ProductVersionPublishedToOffer(String productId, String version,
                                          LocalDate availableFrom, Audit audit) implements DomainEvent {}
    record DescriptionReverted(String productId, String newVersion,
                               String basedOnVersion, Audit audit) implements DomainEvent {}
    record ProductRemovedFromOffer(String productId, Audit audit) implements DomainEvent {}
}
```

### The `Product` (Process) aggregate

Coordinates the three contexts and owns every transition of element 04 RULE-1..4. It is the
mediator of `adapter-mediator.md` — the contexts never call each other.

```java
package com.example.offer.offer;

class Product {                                    // process aggregate, package-private

    private final String productId;
    private final String category;
    private final List<DomainEvent> events;

    private OfferPresence offerPresence;           // PRESENT | REMOVED
    private String visibleVersion;                 // projection of Publications
    private String scheduledVersion;               // projection of Publications
    private DraftState draftState;                 // mirror of the draft, via events
    private Audit lastChange;

    static Product newProduct(String productId, String category, Audit audit) { ... }

    void onDescriptionPublished(String version, LocalDate availableFrom) { ... }
    void onDraftStateChanged(DraftState state) { ... }
    void removeFromOffer(Audit audit) { ... }

    /** Element 04 §10 — derived, never stored. */
    OfferState offerState(Completeness completeness) { ... }
}
```

## 3. `pricing` context

```java
public record Money(BigDecimal value, Currency currency) {
    public Money {
        Objects.requireNonNull(value);
        Objects.requireNonNull(currency);
        if (value.signum() < 0) throw new IllegalArgumentException("money must not be negative");
    }

    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount).setScale(2, RoundingMode.HALF_UP),
                Currency.getInstance(currencyCode));
    }

    /** Applies a discount and rounds once, at the end — RULE-46. */
    public Money discountedBy(Percent percent) {
        BigDecimal factor = BigDecimal.ONE.subtract(percent.value().movePointLeft(2));
        return new Money(value.multiply(factor).setScale(2, RoundingMode.HALF_UP), currency);
    }
}

public record Percent(BigDecimal value) {
    public Percent {
        Objects.requireNonNull(value);
        if (value.signum() <= 0 || value.compareTo(BigDecimal.valueOf(100)) >= 0)
            throw new IllegalArgumentException("percent must be in (0, 100)");                 // RULE-47
        if (value.scale() > 2) throw new IllegalArgumentException("percent scale > 2");
    }

    public static Percent of(String value) { return new Percent(new BigDecimal(value)); }
}

public record DateRange(LocalDate from, LocalDate to) {
    public DateRange {
        Objects.requireNonNull(from);
        if (to != null && to.isBefore(from)) throw new InvalidDateRange(from, to);               // RULE-24
    }

    public static DateRange from(LocalDate from) { return new DateRange(from, null); }           // RULE-23

    public boolean covers(LocalDate date) {                                                      // RULE-38
        return !date.isBefore(from) && (to == null || date.isBefore(to));
    }
}

public enum PriceState { SCHEDULED, ACTIVE, EXPIRED }

public record Price(String priceId, String productId, Money amount, DateRange validity) {
    public PriceState stateAt(LocalDate date) {                                                  // RULE-27
        if (date.isBefore(validity.from())) return PriceState.SCHEDULED;
        return validity.covers(date) ? PriceState.ACTIVE : PriceState.EXPIRED;
    }
    public boolean editableAt(LocalDate date) { return stateAt(date) == PriceState.SCHEDULED; }  // RULE-26
}

public record Discount(String discountId, String productId, Percent percent, DateRange validity) {
    public PriceState stateAt(LocalDate date) {                                                  // RULE-27
        if (date.isBefore(validity.from())) return PriceState.SCHEDULED;
        return validity.covers(date) ? PriceState.ACTIVE : PriceState.EXPIRED;
    }
    public boolean editableAt(LocalDate date) { return stateAt(date) == PriceState.SCHEDULED; }
}
```

### Events

```java
package com.example.offer.pricing;

public interface DomainEvent {
    record ProductPricesChanged(String productId, LocalDate effectiveFrom, Audit audit) implements DomainEvent {}
}
```

### The calculation

```java
public record EffectivePrice(Money amount, String basePriceId, String discountId) {

    /** C2 — element 06. Business date, never a raw instant: a range is calendar-based. */
    static Optional<EffectivePrice> of(List<Price> prices, List<Discount> discounts, LocalDate at) {

        Optional<Price> base = prices.stream()
                .filter(price -> price.validity().covers(at))
                .findFirst();                                                                    // RULE-37
        if (base.isEmpty()) return Optional.empty();                                             // RULE-39

        Optional<Discount> discount = discounts.stream()
                .filter(d -> d.validity().covers(at))
                .findFirst();                                                                    // RULE-25

        Money amount = discount
                .map(d -> base.get().amount().discountedBy(d.percent()))                          // RULE-46
                .orElseGet(() -> base.get().amount());                                            // RULE-45

        return Optional.of(new EffectivePrice(amount, base.get().priceId(),
                discount.map(Discount::discountId).orElse(null)));
    }
}
```

### Aggregate enforcing overlap and editability

```java
class PriceSchedule {                       // one per product, package-private

    private final String productId;
    private final List<DomainEvent> events;
    private List<Price> prices;
    private List<Discount> discounts;
    // no Clock: the business date is resolved by the service and passed in (D1, RULE-67)

    void schedulePrice(Money amount, DateRange validity, LocalDate businessDate, Audit audit) {
        checkNoOverlap(prices.stream().map(Price::validity).toList(), validity);               // RULE-25
        // ... append entry, emit ProductPricesChanged
    }

    void changePrice(String priceId, Money amount, DateRange validity,
                     LocalDate businessDate, Audit audit) {
        Price current = priceById(priceId);
        if (!current.editableAt(businessDate)) throw new PriceNotEditable(priceId);             // RULE-26
        // ...
    }

    /** The price is resolved here and at read time — never a field on the draft (D2, RULE-68). */
    Optional<EffectivePrice> effectivePriceAt(LocalDate businessDate) {
        return EffectivePrice.of(prices, discounts, businessDate);
    }
}
```

---

## 4. Accepted deviations from the blueprint

These are **decisions**, not oversights.

| # | Deviation | Decision | Rationale |
|---|---|---|---|
| D1 | Operations carry an `Audit(who, at)`; no `Clock` is injected into any aggregate | **kept** | the clock is not aggregate state — the caller supplies who and when, and the business date is resolved before the call |
| D2 | `Price.state` is derived (`stateAt(businessDate)`), and `Price` is an aggregate of its own, independent of `DescriptionDraft` | **kept** | a price is managed independently and **resolved at publication / read**; a stored state would drift |
| D3 | `Completeness` is handed into `toDraftSnapshot(...)` instead of being computed by the aggregate | **kept — declared exception** | `Completeness` needs prices from another context; computing it inside would break `adapter-mediator.md` |
| D4 | `Money(BigDecimal value, Currency currency)` rather than the blueprint example's `Money(String currency, BigDecimal amount)` | **kept** | the field names match the API shape (`{"value": "259.00", "currency": "PLN"}`); the blueprint example is illustrative, not a rule |

D3 is the one place this model contradicts an explicit blueprint rule
(`domain-model.md` — *computed fields are populated by the aggregate, not re-computed
outside*). It is accepted because **both** alternatives break a blueprint rule, and the
cross-context boundary is the stronger constraint.

---

## 5. Blueprint compliance

| Convention | Where |
|---|---|
| aggregate package-private, no suffix | `class DescriptionDraft`, `class PriceSchedule` |
| no getters, snapshot instead | `toDraftSnapshot()` — read model only |
| invariants as private checks in the aggregate | `checkEditable`, `checkPending`, `checkSamePerson`, `checkDifferentPerson`, `checkNoOverlap` |
| events nested in `DomainEvent`, past tense, versioned, full value | `BlankDraftCreated_v1`, … |
| `Objects.equals` guard → no event when unchanged | `edit`, `detachPhoto` |
| command DTO with `@Builder`, null = don't update, `apply(...)` | `UpdateDraft` |
| value objects as `public record`, behaviour on the record | `Money.discountedBy`, `DateRange.covers`, `EffectivePrice.of` (cf. `Visibility.basedOn`) |
| policy record, pure, no repositories | `PhotoFormatPolicy` |
| no cross-context imports | `draft` receives a computed `Completeness`; never a `Price` |

## 6. Open

- **Q34** — a decided `ReviewRequest` overwrites `at`, so `submittedAt` is lost from aggregate state; element 02 returns both. Option (a) projection keeps it from `DescriptionPendingReview`; option (b) widen the record.
- **Q35** — answered: `updatedBy` is exposed beside `updatedAt` on `Product` and `DescriptionDraft` (element 02).
- **Q36** — the `Clock` zone must be pinned in `AppConfiguration`, or the audit instant and the pricing business date disagree at day boundaries.
- **Q31** — `Product` (Process) spans `draft`, `offer`, `pricing`: mediator adapter or shared port?
- **Q32** — answered: derived `stateAt(businessDate)` (D2).
