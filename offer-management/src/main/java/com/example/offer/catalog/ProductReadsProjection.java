package com.example.offer.catalog;

import com.example.offer.draft.DraftSnapshot;
import com.example.offer.draft.DraftState;
import com.example.offer.offer.DescriptionVersion;
import com.example.offer.offer.OfferPresence;
import com.example.offer.offer.OfferState;
import com.example.offer.offer.ProductSnapshot;
import com.example.offer.offer.Publication;
import com.example.offer.offer.ScheduledVersion;
import com.example.offer.offer.VisibleVersion;
import com.example.offer.pricing.MoneyView;
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
import java.util.List;
import java.util.Optional;

/**
 * Maintains the product list/detail read model from the three contexts' published events. Every
 * handler merges into the stored row — idempotent by construction — and {@code OfferState} comes
 * from the domain's single derivation {@link OfferState#derive} (element 04 §10, RULE-35/RULE-70),
 * never a copy of its precedence.
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
        if (entity.getTitle() == null) {
            latestVersion(entity);
        }
        recomputeState(entity);
        repository.save(entity);
    }

    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    public void onDraft(DraftSnapshot draft) {
        ProductReadsEntity entity = load(draft.productId());
        entity.setDraftState(draft.state());
        entity.setTitle(draft.title() == null ? entity.getTitle() : draft.title().value());
        entity.setDescriptionVersion(draft.version());
        entity.setPhotoCount(draft.photos().size());
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
        entity.setPrices(prices);
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
        Optional<Publication> scheduled = ScheduledVersion.at(presence, publications, businessDate).version();
        entity.setVisibleVersion(visible.map(Publication::version).orElse(null));
        entity.setAvailableFrom(visible.map(Publication::availableFrom).orElse(null));
        entity.setScheduledVersion(scheduled.map(Publication::version).orElse(null));
        DraftState draftState = entity.getDraftState();
        entity.setState(OfferState.derive(presence, visible.isPresent(), scheduled.isPresent(),
                draftState == DraftState.IN_REVIEW, draftState == DraftState.APPROVED,
                complete(entity.getProductId())));
        applyActivePrice(entity, businessDate);
    }

    private void applyActivePrice(ProductReadsEntity entity, LocalDate businessDate) {
        PriceScheduleSnapshot prices = entity.getPrices();
        entity.setActivePrice(prices == null ? null
                : prices.activePrice(businessDate).map(price -> MoneyView.of(price.amount())).orElse(null));
        entity.setActiveDiscountPercent(prices == null ? null
                : prices.activeDiscount(businessDate)
                        .map(discount -> discount.percent().value().toPlainString()).orElse(null));
    }

    private boolean complete(String productId) {
        return completeness.findById(productId)
                .map(ProductCompletenessEntity::isComplete)
                .orElse(true);
    }

    private static void latestVersion(ProductReadsEntity entity) {
        List<DescriptionVersion> versions = entity.getVersions();
        if (versions == null || versions.isEmpty()) {
            return;
        }
        DescriptionVersion latest = versions.get(versions.size() - 1);
        entity.setTitle(latest.title());
        entity.setDescriptionVersion(latest.version());
        entity.setPhotoCount(latest.photoIds().size());
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
                        builder.like(builder.lower(root.get("category")), like),
                        builder.like(builder.lower(root.get("title")), like)));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static ProductRead read(ProductReadsEntity entity) {
        return new ProductRead(entity.getProductId(), entity.getTitle(), entity.getCategory(), entity.getState(),
                entity.getDescriptionVersion(), entity.getVisibleVersion(), entity.getAvailableFrom(),
                entity.getActivePrice(), entity.getActiveDiscountPercent(), entity.getPhotoCount(),
                entity.getCreatedAt(), entity.getUpdatedAt(), entity.getUpdatedBy());
    }

    private static Instant later(Instant current, Instant candidate) {
        if (current == null) {
            return candidate;
        }
        return candidate != null && candidate.isAfter(current) ? candidate : current;
    }
}
