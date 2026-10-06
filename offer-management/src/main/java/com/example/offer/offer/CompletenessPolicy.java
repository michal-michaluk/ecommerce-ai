package com.example.offer.offer;

import com.example.offer.draft.DraftSnapshot;
import com.example.offer.pricing.PriceScheduleSnapshot;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Element 05: turns content plus the requirement catalogue into the ordered list of missing items.
 * Catalogue-driven (RULE-36); the draft view feeds the gate (RULE-41), the frozen-version view feeds
 * the publish guard (RULE-50); advisory issues stay outside {@code missing}/{@code complete} (RULE-44).
 */
public final class CompletenessPolicy {

    private final RequirementCatalogue catalogue;
    private final TextCheck textCheck;

    public CompletenessPolicy(RequirementCatalogue catalogue, TextCheck textCheck) {
        this.catalogue = Objects.requireNonNull(catalogue, "catalogue is required");
        this.textCheck = Objects.requireNonNull(textCheck, "textCheck is required");
    }

    public static CompletenessPolicy standard() {
        return of(RequirementCatalogue.DEFAULT_CODES);
    }

    /** Builds the policy from the configured, ordered requirement codes (RULE-36). */
    public static CompletenessPolicy of(List<String> codes) {
        return new CompletenessPolicy(RequirementCatalogue.of(codes), new NoTextCheck());
    }

    /** The draft view — what the draft screen and the reviewer gate show (RULE-41). */
    public Completeness evaluate(DraftSnapshot draft, PriceScheduleSnapshot prices, LocalDate businessDate) {
        return evaluate(RequirementContext.of(draft, PricePresence.of(prices, businessDate)),
                textCheck.issues(draft));
    }

    /** The publish-guard view — the frozen version plus the current price state, never a re-derived draft (RULE-50). */
    public Completeness evaluateFrozen(DescriptionVersion version, PriceScheduleSnapshot prices,
                                       LocalDate businessDate) {
        return evaluate(RequirementContext.of(version, PricePresence.of(prices, businessDate)), List.of());
    }

    Completeness evaluate(DraftSnapshot draft, PricePresence price) {
        return evaluate(RequirementContext.of(draft, price), textCheck.issues(draft));
    }

    Completeness evaluate(RequirementContext context) {
        return evaluate(context, List.of());
    }

    private Completeness evaluate(RequirementContext context, List<TextIssue> issues) {
        List<Completeness.MissingRequirement> missing = catalogue.requirements().stream()
                .filter(requirement -> !requirement.satisfied().test(context))
                .map(requirement -> new Completeness.MissingRequirement(requirement.code(), requirement.label()))
                .toList();
        return new Completeness(missing.isEmpty(), missing, issues);
    }
}
