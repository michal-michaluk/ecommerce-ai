package com.example.offer.catalog;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/** Read-side repository of the catalog projection — never touched by the write model. */
@Repository
interface ProductReadsRepository extends CrudRepository<ProductReadsEntity, String>,
        JpaSpecificationExecutor<ProductReadsEntity> {
}
