package com.example.offer.catalog;

import com.example.offer.draft.DraftState;
import com.example.offer.offer.DescriptionVersion;
import com.example.offer.offer.OfferPresence;
import com.example.offer.offer.OfferState;
import com.example.offer.offer.Publication;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

/**
 * The product list/detail read model. It stores the offer inputs (presence, publications) and the
 * draft inputs (state, category) so {@code state} is recomputed on every event rather than trusted.
 */
@Entity
@Table(name = "product_reads")
@Getter
@Setter
@NoArgsConstructor
class ProductReadsEntity {

    @Id
    private String productId;

    private String category;

    @Enumerated(EnumType.STRING)
    private OfferState state;

    private String visibleVersion;
    private String scheduledVersion;
    private Instant updatedAt;
    private String updatedBy;
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    private OfferPresence offerPresence;

    @Enumerated(EnumType.STRING)
    private DraftState draftState;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<Publication> publications = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    private List<DescriptionVersion> versions = List.of();

    ProductReadsEntity(String productId) {
        this.productId = productId;
    }
}
