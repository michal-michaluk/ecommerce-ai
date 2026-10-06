package com.example.offer.offer;

import com.example.offer.auth.Audit;
import com.example.offer.auth.Identity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

final class OfferFixture {

    static final String PRODUCT_ID = "p-2019-0442";
    static final Instant AT = Instant.parse("2019-10-01T09:00:00Z");
    static final Identity MANAGER = new Identity("a.kowalska");
    static final Identity REVIEWER = new Identity("m.nowak");

    private OfferFixture() {
    }

    static Audit audit() {
        return new Audit(MANAGER, AT);
    }

    static Audit laterAudit() {
        return new Audit(MANAGER, AT.plusSeconds(3600));
    }

    static LocalDate date(String value) {
        return LocalDate.parse(value);
    }

    static Product givenProduct() {
        return Product.newProduct(PRODUCT_ID, audit());
    }

    static DescriptionVersion version(String version) {
        return version(version, null);
    }

    static DescriptionVersion version(String version, String basedOnVersion) {
        return new DescriptionVersion(PRODUCT_ID, version, "Kosiarka ręczna 340",
                "Solidna kosiarka ręczna do trawy i chwastów.", Map.of("category", "Ogród"),
                List.of("ph-1", "ph-2"), basedOnVersion, REVIEWER, AT);
    }

    static Product givenApprovedProduct() {
        Product product = givenProduct();
        product.changeDraftState(DraftState.APPROVED, laterAudit());
        return product;
    }

    static Product givenPublishedProduct(String version, LocalDate availableFrom, LocalDate businessDate) {
        Product product = givenApprovedProduct();
        product.publish("pub-" + version, version(version), availableFrom, businessDate,
                List.of(), laterAudit());
        return product;
    }

    static Publication publication(String publicationId, String version, String availableFrom) {
        return Publication.schedule(publicationId, PRODUCT_ID, version,
                availableFrom == null ? null : date(availableFrom), date("2019-01-01"), audit());
    }
}
