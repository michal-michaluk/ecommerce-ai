package com.example.offer.offer;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

/**
 * The mandatory-item catalogue (RULE-36). It is data: the enabled codes and their order come from
 * configuration, the calculation in {@link CompletenessPolicy} never names an item.
 */
record RequirementCatalogue(List<Requirement> requirements) {

    static final List<String> DEFAULT_CODES =
            List.of("TITLE_REQUIRED", "DESCRIPTION_REQUIRED", "PHOTO_REQUIRED", "PRICE_REQUIRED");

    private static final Map<String, Requirement> REGISTRY = Map.of(
            "TITLE_REQUIRED", new Requirement("TITLE_REQUIRED", "Title", RequirementContext::hasTitle),
            "DESCRIPTION_REQUIRED", new Requirement("DESCRIPTION_REQUIRED", "Description",
                    RequirementContext::hasDescription),
            "PHOTO_REQUIRED", new Requirement("PHOTO_REQUIRED", "At least one photo",
                    RequirementContext::hasPhoto),
            "PRICE_REQUIRED", new Requirement("PRICE_REQUIRED", "Price valid for a date range",
                    RequirementContext::hasPrice));

    record Requirement(String code, String label, Predicate<RequirementContext> satisfied) {
        Requirement {
            requireNonNull(code, "code is required");
            requireNonNull(label, "label is required");
            requireNonNull(satisfied, "predicate is required");
        }
    }

    RequirementCatalogue {
        requirements = List.copyOf(requirements);
    }

    static RequirementCatalogue standard() {
        return of(DEFAULT_CODES);
    }

    /** Configuration entry point: the ordered enabled codes select and order the catalogue (RULE-36, RULE-43). */
    static RequirementCatalogue of(List<String> codes) {
        return new RequirementCatalogue(codes.stream().map(RequirementCatalogue::definition).toList());
    }

    private static Requirement definition(String code) {
        Requirement requirement = REGISTRY.get(code);
        if (requirement == null) {
            throw new IllegalArgumentException("unknown requirement code " + code);
        }
        return requirement;
    }
}
