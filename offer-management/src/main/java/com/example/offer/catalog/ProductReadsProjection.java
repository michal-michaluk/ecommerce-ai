package com.example.offer.catalog;

import com.example.offer.draft.DraftSnapshot;
import com.example.offer.draft.DraftState;
import com.example.offer.offer.OfferPresence;
import com.example.offer.offer.OfferState;
import com.example.offer.offer.ProductSnapshot;
import com.example.offer.offer.Publication;
import com.example.offer.offer.PublicationState;
import com.example.offer.offer.VisibleVersion;
import com.example.offer.pricing.PriceScheduleSnapshot;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Maintains the product list/detail read model from the three contexts' published events. Every
 * handler merges into the stored row — idempotent by construction — and {@code OfferState} is
 * recomputed from the stored offer/draft inputs plus the stored completeness, never taken from the
 * event (element 04 §10, RULE-70).
 */
@Component
@Transactional
@RequiredArgsConstructor
class ProductReadsProjection {

    private final ProductReadsRepository repository;
    private final ProductCompletenessRepository completeness;
    private final Clock clock;

    @EventListener
    public void onProduct(ProductSnapshot product) {
        ProductReadsEntity entity = load(product.productId());
        entity.setOfferPresence(product.offerPresence());
        entity.setPublications(product.publications());
        entity.setVersions(product.versions());
        entity.setCreatedAt(product.createdAt());
        entity.setUpdatedBy(product.lastChange().who().subject());
        entity.setUpdatedAt(later(entity.getUpdatedAt(), product.lastChange().at()));
        recomputeState(entity);
        repository.save(entity);
    }

    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    public void onDraft(DraftSnapshot draft) {
        ProductReadsEntity entity = load(draft.productId());
        entity.setDraftState(draft.state());
        entity.setCategory(draft.attributes() == null ? null : draft.attributes().category());
        entity.setUpdatedBy(draft.lastChange().who().subject());
        entity.setUpdatedAt(later(entity.getUpdatedAt(), draft.lastChange().at()));
        recomputeState(entity);
        repository.save(entity);
    }

    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    public void onPrices(PriceScheduleSnapshot prices) {
        ProductReadsEntity entity = load(prices.productId());
        recomputeState(entity);
        repository.save(entity);
    }

    @Transactional(readOnly = true)
    public Page<ProductRead> list(OfferState state, String query, Pageable pageable) {
        return repository.findAll(filter(state, query), pageable).map(ProductReadsProjection::read);
    }

    @Transactional(readOnly = true)
    public Optional<ProductRead> find(String productId) {
        return repository.findById(productId).map(ProductReadsProjection::read);
    }

    private ProductReadsEntity load(String productId) {
        return repository.findById(productId).orElseGet(() -> new ProductReadsEntity(productId));
    }

    private void recomputeState(ProductReadsEntity entity) {
        OfferPresence presence = entity.getOfferPresence() == null
                ? OfferPresence.PRESENT : entity.getOfferPresence();
        entity.setOfferPresence(presence);
        LocalDate businessDate = LocalDate.now(clock);
        List<Publication> publications = entity.getPublications() == null ? List.of() : entity.getPublications();
        Optional<Publication> visible = VisibleVersion.at(presence, publications, businessDate).version();
        Optional<Publication> scheduled = scheduledAt(presence, publications, businessDate);
        entity.setVisibleVersion(visible.map(Publication::version).orElse(null));
        entity.setScheduledVersion(scheduled.map(Publication::version).orElse(null));
        entity.setState(offerState(presence, visible.isPresent(), scheduled.isPresent(),
                entity.getDraftState(), complete(entity.getProductId())));
    }

    private boolean complete(String productId) {
        return completeness.findById(productId)
                .map(ProductCompletenessEntity::isComplete)
                .orElse(true);
    }

    private static OfferState offerState(OfferPresence presence, boolean visible, boolean scheduled,
                                         DraftState draftState, boolean complete) {
        if (presence == OfferPresence.REMOVED) {
            return OfferState.REMOVED;
        }
        if (visible) {
            return OfferState.PUBLISHED;
        }
        if (scheduled) {
            return OfferState.SCHEDULED;
        }
        if (draftState == DraftState.IN_REVIEW) {
            return OfferState.PENDING_REVIEW;
        }
        if (draftState == DraftState.APPROVED && !complete) {
            return OfferState.BLOCKED;
        }
        return OfferState.DRAFT;
    }

    private static Optional<Publication> scheduledAt(OfferPresence presence, List<Publication> publications,
                                                     LocalDate businessDate) {
        if (presence == OfferPresence.REMOVED) {
            return Optional.empty();
        }
        return publications.stream()
                .filter(publication -> publication.stateAt(businessDate) == PublicationState.SCHEDULED)
                .min(Comparator.comparing(Publication::availableFrom));
    }

    private static Specification<ProductReadsEntity> filter(OfferState state, String query) {
        return (root, criteriaQuery, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (state != null) {
                predicates.add(builder.equal(root.get("state"), state));
            }
            if (query != null && !query.isBlank()) {
                String like = "%" + query.strip().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("productId")), like),
                        builder.like(builder.lower(root.get("category")), like)));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static ProductRead read(ProductReadsEntity entity) {
        return new ProductRead(entity.getProductId(), entity.getCategory(), entity.getState(),
                entity.getVisibleVersion(), entity.getScheduledVersion(),
                entity.getUpdatedAt(), entity.getUpdatedBy());
    }

    private static Instant later(Instant current, Instant candidate) {
        if (current == null) {
            return candidate;
        }
        return candidate != null && candidate.isAfter(current) ? candidate : current;
    }
}
