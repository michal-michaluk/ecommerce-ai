package com.example.offer.catalog;

import com.example.offer.draft.DraftSnapshot;
import com.example.offer.draft.DraftState;
import com.example.offer.draft.ReviewRequest;
import com.example.offer.offer.Completeness;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Maintains the review queue from draft events. A pending review stores its {@code submittedAt};
 * once decided, the decision moment is recorded but the original {@code submittedAt} is preserved
 * (Q34). Handlers merge by {@code reviewRequestId}, so a replay is harmless.
 */
@Component
@Transactional
@RequiredArgsConstructor
class ReviewQueueProjection {

    private static final String PENDING = "PENDING";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";

    private final ReviewQueueRepository repository;
    private final ProductCompletenessRepository completeness;

    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    public void onDraft(DraftSnapshot draft) {
        ReviewRequest review = draft.review();
        if (review == null) {
            return;
        }
        ReviewQueueEntity entity = repository.findById(review.reviewRequestId())
                .orElseGet(() -> new ReviewQueueEntity(review.reviewRequestId()));
        entity.setProductId(draft.productId());
        entity.setProductTitle(draft.title() == null ? null : draft.title().value());
        entity.setDescriptionVersion(draft.version());
        entity.setAuthor(review.author().subject());
        entity.setMissingCount(missingCount(draft.productId()));
        if (review.isPending()) {
            entity.setStatus(PENDING);
            entity.setSubmittedAt(review.at());
            entity.setDecidedBy(null);
            entity.setDecidedAt(null);
        } else {
            entity.setStatus(draft.state() == DraftState.APPROVED ? APPROVED : REJECTED);
            entity.setDecidedBy(review.decidedBy().subject());
            entity.setDecidedAt(review.at());
            if (entity.getSubmittedAt() == null) {
                entity.setSubmittedAt(review.at());
            }
        }
        repository.save(entity);
    }

    @Transactional(readOnly = true)
    public Page<ReviewRequestRead> list(String status, Pageable pageable) {
        Page<ReviewQueueEntity> page = status == null || status.isBlank()
                ? repository.findAll(pageable)
                : repository.findByStatus(status, pageable);
        return page.map(ReviewQueueProjection::read);
    }

    @Transactional(readOnly = true)
    public Optional<ReviewRequestRead> find(String reviewRequestId) {
        return repository.findById(reviewRequestId).map(ReviewQueueProjection::read);
    }

    @Transactional(readOnly = true)
    public Optional<ReviewDetail> findDetail(String reviewRequestId) {
        return repository.findById(reviewRequestId).map(this::detail);
    }

    private ReviewDetail detail(ReviewQueueEntity entity) {
        ProductCompletenessEntity completeness = this.completeness.findById(entity.getProductId()).orElse(null);
        List<Completeness.MissingRequirement> missing = completeness == null || completeness.getMissing() == null
                ? List.of() : completeness.getMissing();
        boolean blocked = completeness != null && !completeness.isComplete();
        DraftSnapshot draft = completeness == null ? null : completeness.getDraft();
        String title = draft != null && draft.title() != null ? draft.title().value() : entity.getProductTitle();
        String description = draft != null && draft.description() != null ? draft.description().value() : null;
        ReviewDetail.Decision decision = entity.getDecidedBy() == null ? null
                : new ReviewDetail.Decision(entity.getStatus(), entity.getDecidedBy(), entity.getDecidedAt(),
                        entity.getReason());
        return new ReviewDetail(entity.getReviewRequestId(), entity.getProductId(), entity.getDescriptionVersion(),
                entity.getStatus(), entity.getAuthor(), entity.getSubmittedAt(),
                new ReviewDetail.Gate(blocked, missing, List.of()),
                new ReviewDetail.Preview(title, description, wordCount(description),
                        draft == null ? 0 : draft.photos().size(), null),
                decision);
    }

    private static int wordCount(String description) {
        return description == null || description.isBlank() ? 0 : description.strip().split("\\s+").length;
    }

    private int missingCount(String productId) {
        return completeness.findById(productId)
                .map(entity -> entity.getMissing() == null ? 0 : entity.getMissing().size())
                .orElse(0);
    }

    private static ReviewRequestRead read(ReviewQueueEntity entity) {
        return new ReviewRequestRead(entity.getReviewRequestId(), entity.getProductId(),
                entity.getProductTitle(), entity.getDescriptionVersion(), entity.getStatus(),
                entity.getAuthor(), entity.getSubmittedAt(), entity.getMissingCount(),
                entity.getDecidedBy(), entity.getDecidedAt());
    }
}
