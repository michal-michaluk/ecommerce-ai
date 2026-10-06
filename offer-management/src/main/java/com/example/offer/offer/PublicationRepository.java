package com.example.offer.offer;

import java.util.List;
import java.util.Optional;

/** Secondary port: publications are append-only; cancellation persists a new state (RULE-33). */
interface PublicationRepository {

    Optional<Publication> get(String publicationId);

    List<Publication> forProduct(String productId);

    void save(Publication publication);
}
