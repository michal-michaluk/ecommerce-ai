package com.example.offer.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** The pending/decided review-request read model (element 02 screens 05/06). */
@Entity
@Table(name = "review_queue")
@Getter
@Setter
@NoArgsConstructor
class ReviewQueueEntity {

    @Id
    private String reviewRequestId;

    private String productId;
    private String productTitle;
    private String descriptionVersion;
    private String status;
    private String author;
    private Instant submittedAt;
    private String decidedBy;
    private Instant decidedAt;
    private int missingCount;
    private String reason;

    ReviewQueueEntity(String reviewRequestId) {
        this.reviewRequestId = reviewRequestId;
    }
}
