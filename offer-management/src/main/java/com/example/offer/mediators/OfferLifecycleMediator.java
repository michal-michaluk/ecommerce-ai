package com.example.offer.mediators;

import com.example.offer.auth.Audit;
import com.example.offer.draft.Description;
import com.example.offer.draft.DraftAttributes;
import com.example.offer.draft.DraftService;
import com.example.offer.draft.DraftSnapshot;
import com.example.offer.draft.DraftState;
import com.example.offer.draft.Photo;
import com.example.offer.draft.ReviewRequest;
import com.example.offer.draft.Title;
import com.example.offer.draft.UpdateDraft;
import com.example.offer.offer.Completeness;
import com.example.offer.offer.CompletenessPolicy;
import com.example.offer.offer.Decisions;
import com.example.offer.offer.DescriptionVersion;
import com.example.offer.offer.OfferService;
import com.example.offer.offer.ProductSnapshot;
import com.example.offer.pricing.PriceScheduleSnapshot;
import com.example.offer.pricing.PricingService;
import com.example.offer.publishing.IntegrationEvent.PhotoView;
import com.example.offer.publishing.IntegrationEvent.ProductRemovedFromOffer;
import com.example.offer.publishing.IntegrationEvent.ProductVersionPublishedToOffer;
import com.example.offer.publishing.Outbox;
import com.example.offer.tools.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cross-context adapter for the offer lifecycle (E04 §2): it orchestrates, it never decides.
 * Every decision comes from {@link Decisions}, every state change from a public context port.
 */
@Component
@Transactional
@RequiredArgsConstructor
public class OfferLifecycleMediator {

    private final DraftService drafts;
    private final PricingService prices;
    private final OfferService offers;
    private final CompletenessPolicy completeness;
    private final Decisions decisions;
    private final Outbox outbox;
    private final Clock clock;

    public void createProduct(String productId, String version, Title title, Audit audit) {
        offers.create(productId, audit);
        drafts.create(productId, version, title, audit);
    }

    public void editDraft(String productId, UpdateDraft update, Audit audit) {
        drafts.edit(productId, update, audit);
    }

    public void attachPhoto(String productId, Photo photo, Audit audit) {
        drafts.attachPhoto(productId, photo, audit);
    }

    public void requestReview(String productId, ReviewRequest request, LocalDate businessDate, Audit audit) {
        DraftSnapshot draft = draft(productId);
        Completeness current = completeness.evaluate(draft, priceSchedule(productId), businessDate);
        require(decisions.reviewRequestGuard(true, draft.state() == DraftState.IN_REVIEW,
                current.missing().size()));
        drafts.requestReview(productId, request, audit);
        offers.changeDraftState(productId, com.example.offer.offer.DraftState.IN_REVIEW, audit);
    }

    public void approve(String productId, ReviewRequest decision, Audit audit) {
        ReviewRequest review = draft(productId).review();
        require(decisions.separationOfDuties(review != null && review.isPending(),
                review == null ? null : review.author(), decision.decidedBy()));
        drafts.approve(productId, decision, audit);
        offers.changeDraftState(productId, com.example.offer.offer.DraftState.APPROVED, audit);
    }

    public void reject(String productId, ReviewRequest decision, String reason, Audit audit) {
        ReviewRequest review = draft(productId).review();
        require(decisions.separationOfDuties(review != null && review.isPending(),
                review == null ? null : review.author(), decision.decidedBy()));
        drafts.reject(productId, decision, reason, audit);
        offers.changeDraftState(productId, com.example.offer.offer.DraftState.EDITING, audit);
    }

    /** Approves the review named by its id — the frontend decision endpoint is keyed by review, not product. */
    public ReviewRequest approveReview(String reviewRequestId, Audit audit) {
        String productId = reviewProduct(reviewRequestId);
        ReviewRequest review = draft(productId).review();
        ReviewRequest decision = new ReviewRequest(reviewRequestId, review.author(), audit.who(), audit.at());
        approve(productId, decision, audit);
        return decision;
    }

    /** Rejects the review named by its id, carrying the reviewer's reason. */
    public ReviewRequest rejectReview(String reviewRequestId, String reason, Audit audit) {
        String productId = reviewProduct(reviewRequestId);
        ReviewRequest review = draft(productId).review();
        ReviewRequest decision = new ReviewRequest(reviewRequestId, review.author(), audit.who(), audit.at());
        reject(productId, decision, reason, audit);
        return decision;
    }

    private String reviewProduct(String reviewRequestId) {
        String productId = drafts.productIdOfReview(reviewRequestId)
                .orElseThrow(() -> new DecisionDenied(ErrorCode.REVIEW_NOT_FOUND));
        ReviewRequest review = draft(productId).review();
        if (review == null || !review.reviewRequestId().equals(reviewRequestId)) {
            throw new DecisionDenied(ErrorCode.REVIEW_NOT_FOUND);
        }
        return productId;
    }

    /** Freezes the approved draft into an immutable version (E04 §2 step 7). */
    public DescriptionVersion freeze(String productId, Audit audit) {
        DraftSnapshot draft = draft(productId);
        ReviewRequest review = draft.review();
        if (draft.state() != DraftState.APPROVED || review == null || review.decidedBy() == null) {
            throw new DecisionDenied(ErrorCode.VERSION_NOT_APPROVED);
        }
        return offers.freeze(freeze(productId, draft, review, audit), audit);
    }

    /**
     * Publishes a frozen version (E04 §2 step 8). D2 reads the frozen version plus the current price
     * state — never a re-derived draft (RULE-50); the most specific failure wins.
     */
    public void publish(String productId, String publicationId, DescriptionVersion version,
                        LocalDate availableFrom, LocalDate businessDate, Audit audit) {
        DraftSnapshot draft = draft(productId);
        boolean versionExists = version != null && offers.versionExists(productId, version.version());
        Completeness frozen = versionExists
                ? completeness.evaluateFrozen(version, priceSchedule(productId), businessDate)
                : new Completeness(false, List.of(), List.of());
        require(decisions.publishGuard(versionExists, draft.state() == DraftState.APPROVED, frozen));

        ProductSnapshot product = offers.get(productId, businessDate)
                .orElseThrow(() -> new DecisionDenied(ErrorCode.PRODUCT_NOT_FOUND));
        LocalDate createdOn = LocalDate.ofInstant(product.createdAt(), clock.getZone());
        require(decisions.publicationTiming(availableFrom, businessDate, createdOn));

        offers.publish(productId, publicationId, version, availableFrom, businessDate, missingCodes(frozen), audit);
        outbox.append(new ProductVersionPublishedToOffer(productId, version.version(), availableFrom,
                version.title(), version.description(), version.attributes(), photoViews(version, draft), audit));
    }

    public DraftSnapshot revert(String productId, String basedOnVersion, String newVersion,
                               LocalDate businessDate, Audit audit) {
        ProductSnapshot product = offers.get(productId, businessDate)
                .orElseThrow(() -> new DecisionDenied(ErrorCode.PRODUCT_NOT_FOUND));
        DescriptionVersion base = product.versions().stream()
                .filter(version -> version.version().equals(basedOnVersion))
                .findFirst()
                .orElseThrow(() -> new DecisionDenied(ErrorCode.VERSION_NOT_FOUND));
        List<Photo> carriedPhotos = draft(productId).photos().stream()
                .filter(photo -> base.photoIds().contains(photo.photoId()))
                .toList();

        offers.revert(productId, newVersion, basedOnVersion, audit);
        drafts.create(productId, newVersion, new Title(base.title()), audit);
        if (base.description() != null) {
            drafts.edit(productId, UpdateDraft.builder().description(new Description(base.description())).build(), audit);
        }
        carriedPhotos.forEach(photo -> drafts.attachPhoto(productId, photo, audit));
        return draft(productId);
    }

    public void removeFromOffer(String productId, Audit audit) {
        offers.removeFromOffer(productId, audit);
        outbox.append(new ProductRemovedFromOffer(productId, audit));
    }

    private static List<PhotoView> photoViews(DescriptionVersion version, DraftSnapshot draft) {
        Map<String, Photo> photos = new LinkedHashMap<>();
        draft.photos().forEach(photo -> photos.put(photo.photoId(), photo));
        return version.photoIds().stream()
                .map(photos::get)
                .map(photo -> new PhotoView(photo.photoId(), photo.mime(), photo.width(), photo.height()))
                .toList();
    }

    private PriceScheduleSnapshot priceSchedule(String productId) {
        return prices.get(productId).orElse(null);
    }

    private DraftSnapshot draft(String productId) {
        return drafts.get(productId).orElseThrow(() -> new DecisionDenied(ErrorCode.PRODUCT_NOT_FOUND));
    }

    private static DescriptionVersion freeze(String productId, DraftSnapshot draft, ReviewRequest review,
                                             Audit audit) {
        return new DescriptionVersion(productId, draft.version(), draft.title().value(),
                draft.description() == null ? null : draft.description().value(),
                attributes(draft.attributes()),
                draft.photos().stream().map(Photo::photoId).toList(),
                draft.basedOnVersion(), review.decidedBy(), audit.at());
    }

    private static Map<String, String> attributes(DraftAttributes attributes) {
        Map<String, String> values = new LinkedHashMap<>();
        if (attributes != null) {
            if (attributes.category() != null) {
                values.put("category", attributes.category());
            }
            if (attributes.manualUrl() != null) {
                values.put("manualUrl", attributes.manualUrl());
            }
        }
        return Map.copyOf(values);
    }

    private static List<String> missingCodes(Completeness completeness) {
        return completeness.missing().stream()
                .map(Completeness.MissingRequirement::code)
                .toList();
    }

    private static <T> T require(Decisions.Decision<T> decision) {
        if (!decision.isAllowed()) {
            throw new DecisionDenied(decision.denial(), decision.blocking());
        }
        return decision.value();
    }

    /** The draft read behind the draft workspace; the product-not-found contract code travels here. */
    public DraftSnapshot draftOrThrow(String productId) {
        return draft(productId);
    }

    /** The completeness of the current draft at a business date — the server owns the rules (P1). */
    public Completeness completenessOf(String productId, LocalDate businessDate) {
        return completenessOf(draft(productId), productId, businessDate);
    }

    public Completeness completenessOf(DraftSnapshot draft, String productId, LocalDate businessDate) {
        return completeness.evaluate(draft, priceSchedule(productId), businessDate);
    }

    public ProductSnapshot productOrThrow(String productId, LocalDate businessDate) {
        return offers.get(productId, businessDate)
                .orElseThrow(() -> new DecisionDenied(ErrorCode.PRODUCT_NOT_FOUND));
    }

    /**
     * Publishes the requested version of an approved draft: the version must be the approved
     * draft's own version, it is frozen and published in one orchestrated step (E04 step 7–8).
     */
    public ProductSnapshot publishVersion(String productId, String publicationId, String version,
                                          LocalDate availableFrom, LocalDate businessDate, Audit audit) {
        DraftSnapshot draft = draft(productId);
        require(decisions.publishGuard(true, draft.state() == DraftState.APPROVED
                && draft.version().equals(version), new Completeness(true, List.of(), List.of())));
        publish(productId, publicationId, freeze(productId, audit), availableFrom, businessDate, audit);
        return productOrThrow(productId, businessDate);
    }

    public ProductSnapshot cancelPublication(String productId, String publicationId,
                                             LocalDate businessDate, Audit audit) {
        return offers.cancelPublication(productId, publicationId, businessDate, audit);
    }
}
