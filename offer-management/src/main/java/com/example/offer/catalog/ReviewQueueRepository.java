package com.example.offer.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/** Read-side repository of the review queue projection. */
@Repository
interface ReviewQueueRepository extends CrudRepository<ReviewQueueEntity, String> {

    Page<ReviewQueueEntity> findByStatus(String status, Pageable pageable);

    Page<ReviewQueueEntity> findAll(Pageable pageable);
}
