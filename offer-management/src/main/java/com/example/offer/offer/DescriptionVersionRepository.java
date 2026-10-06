package com.example.offer.offer;

import java.util.List;
import java.util.Optional;

/** Secondary port: description versions are immutable and appended, never updated (RULE-9). */
interface DescriptionVersionRepository {

    Optional<DescriptionVersion> get(String productId, String version);

    List<DescriptionVersion> forProduct(String productId);

    void save(DescriptionVersion version);
}
