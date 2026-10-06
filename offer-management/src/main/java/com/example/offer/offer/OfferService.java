package com.example.offer.offer;

import com.example.offer.auth.Audit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Primary port of the offer context: owns the {@link Product} lifecycle. Cross-context flows
 * orchestrate through this service, never through the aggregate (adapter-mediator.md).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class OfferService {

    private final ProductRepository products;
    private final DescriptionVersionRepository versions;
    private final PublicationRepository publications;

    public ProductSnapshot create(String productId, Audit audit) {
        Product product = Product.newProduct(productId, audit);
        products.save(product);
        return product.toSnapshot();
    }

    public Optional<ProductSnapshot> get(String productId, LocalDate businessDate) {
        return products.get(productId).map(product -> product.toSnapshot(businessDate));
    }

    public ProductSnapshot changeDraftState(String productId, DraftState state, Audit audit) {
        Product product = load(productId);
        product.changeDraftState(state, audit);
        products.save(product);
        return product.toSnapshot();
    }

    /** Freezes an approved draft into an immutable version, stored independently of the process state (RULE-9). */
    public DescriptionVersion freeze(DescriptionVersion version, Audit audit) {
        versions.save(version);
        return version;
    }

    public boolean versionExists(String productId, String version) {
        return versions.get(productId, version).isPresent();
    }

    public ProductSnapshot publish(String productId, String publicationId, DescriptionVersion version,
                                   LocalDate availableFrom, LocalDate businessDate,
                                   List<String> missingRequirements, Audit audit) {
        Product product = load(productId);
        product.publish(publicationId, version, availableFrom, businessDate, missingRequirements, audit);
        products.save(product);
        product.toSnapshot().publications().stream()
                .filter(publication -> publication.publicationId().equals(publicationId))
                .findFirst()
                .ifPresent(publications::save);
        return product.toSnapshot(businessDate);
    }

    public ProductSnapshot revert(String productId, String newVersion, String basedOnVersion, Audit audit) {
        Product product = load(productId);
        product.revert(newVersion, basedOnVersion, audit);
        products.save(product);
        return product.toSnapshot();
    }

    public ProductSnapshot cancelPublication(String productId, String publicationId,
                                             LocalDate businessDate, Audit audit) {
        Product product = load(productId);
        product.cancelPublication(publicationId, businessDate, audit);
        products.save(product);
        product.toSnapshot().publications().stream()
                .filter(publication -> publication.publicationId().equals(publicationId))
                .findFirst()
                .ifPresent(publications::save);
        return product.toSnapshot();
    }

    public ProductSnapshot removeFromOffer(String productId, Audit audit) {
        Product product = load(productId);
        product.removeFromOffer(audit);
        products.save(product);
        return product.toSnapshot();
    }

    private Product load(String productId) {
        return products.get(productId).orElseThrow(() -> new ProductNotFound(productId));
    }
}
