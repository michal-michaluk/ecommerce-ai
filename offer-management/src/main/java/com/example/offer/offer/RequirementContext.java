package com.example.offer.offer;

import com.example.offer.draft.DraftSnapshot;

/** The four facts element 05 evaluates: content plus price presence, from the draft or a frozen version. */
record RequirementContext(String title, String description, int photoCount, PricePresence price) {

    static RequirementContext of(DraftSnapshot draft, PricePresence price) {
        return new RequirementContext(
                draft.title() == null ? null : draft.title().value(),
                draft.description() == null ? null : draft.description().value(),
                draft.photos().size(),
                price);
    }

    static RequirementContext of(DescriptionVersion version, PricePresence price) {
        return new RequirementContext(version.title(), version.description(),
                version.photoIds().size(), price);
    }

    boolean hasTitle() {
        return !blank(title);
    }

    boolean hasDescription() {
        return !blank(description);
    }

    boolean hasPhoto() {
        return photoCount >= 1;
    }

    boolean hasPrice() {
        return price.present();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
