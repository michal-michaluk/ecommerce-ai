package com.example.offer.catalog;

import com.example.offer.draft.DraftSnapshot;
import com.example.offer.offer.Completeness;
import com.example.offer.offer.CompletenessPolicy;
import com.example.offer.pricing.PriceScheduleSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Keeps the stored completeness projection (A8) current from draft/photo and price events. The
 * output is always recomputed from the requirement catalogue (RULE-36, RULE-43) — a passed value is
 * never trusted, so replaying an event is harmless.
 */
@Component
@Transactional
@RequiredArgsConstructor
class ProductCompletenessProjection {

    private final ProductCompletenessRepository repository;
    private final CompletenessPolicy policy;
    private final Clock clock;

    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void onDraft(DraftSnapshot draft) {
        ProductCompletenessEntity entity = repository.findById(draft.productId())
                .orElseGet(() -> new ProductCompletenessEntity(draft.productId()));
        entity.setDraft(draft);
        recompute(entity);
        repository.save(entity);
    }

    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void onPrices(PriceScheduleSnapshot prices) {
        ProductCompletenessEntity entity = repository.findById(prices.productId())
                .orElseGet(() -> new ProductCompletenessEntity(prices.productId()));
        entity.setPrices(prices);
        recompute(entity);
        repository.save(entity);
    }

    @Transactional(readOnly = true)
    public Optional<ProductCompletenessEntity> get(String productId) {
        return repository.findById(productId);
    }

    private void recompute(ProductCompletenessEntity entity) {
        if (entity.getDraft() == null) {
            entity.setComplete(false);
            entity.setMissing(List.of());
            return;
        }
        Completeness completeness = policy.evaluate(entity.getDraft(), entity.getPrices(), LocalDate.now(clock));
        entity.setComplete(completeness.complete());
        entity.setMissing(completeness.missing());
    }
}
