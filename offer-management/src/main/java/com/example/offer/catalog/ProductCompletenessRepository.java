package com.example.offer.catalog;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/** Read-side repository of the stored completeness projection (A8). */
@Repository
interface ProductCompletenessRepository extends CrudRepository<ProductCompletenessEntity, String> {
}
