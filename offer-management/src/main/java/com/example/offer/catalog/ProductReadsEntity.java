package com.example.offer.catalog;

import com.example.offer.draft.DraftState;
import com.example.offer.offer.DescriptionVersion;
import com.example.offer.offer.OfferPresence;
import com.example.offer.offer.OfferState;
import com.example.offer.offer.Publication;
import com.example.offer.pricing.MoneyView;
import com.example.offer.pricing.PriceScheduleSnapshot;
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
import java.time.LocalDate;
import java.util.List;

/**
 * The product list/detail read model. It stores the offer inputs (presence, publications), the
 * draft inputs (title, version, photos, state, category) and the price schedule so the declared
 * shape is recomputed on every event rather than trusted.
 */
@Entity
@Table(name = "product_reads")
@Getter
@Setter
@NoArgsConstructor
class ProductReadsEntity {

    @Id
    private String productId;

    private String title;
    private String category;

    @Enumerated(EnumType.STRING)
    private OfferState state;

    private String descriptionVersion;
    private String visibleVersion;
    private String scheduledVersion;
    private LocalDate availableFrom;

    @JdbcTypeCode(SqlTypes.JSON)
    private MoneyView activePrice;

    private String activeDiscountPercent;
    private int photoCount;

    private Instant createdAt;
    private Instant updatedAt;
    private String updatedBy;

    @Enumerated(EnumType.STRING)
    private OfferPresence offerPresence;

    @Enumerated(EnumType.STRING)
    private DraftState draftState;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<Publication> publications = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    private List<DescriptionVersion> versions = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    private PriceScheduleSnapshot prices;

    ProductReadsEntity(String productId) {
        this.productId = productId;
    }
}
