package com.example.offer.catalog;

import com.example.offer.draft.DraftSnapshot;
import com.example.offer.offer.Completeness;
import com.example.offer.pricing.PriceScheduleSnapshot;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

/**
 * The stored completeness read model (A8): {@code complete} plus the ordered {@code missing[]} in
 * catalogue order. It keeps the draft and price inputs so the projection recomputes rather than
 * trusting a passed value.
 */
@Entity
@Table(name = "product_completeness")
@Getter
@Setter
@NoArgsConstructor
class ProductCompletenessEntity {

    @Id
    private String productId;

    private boolean complete;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<Completeness.MissingRequirement> missing = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    private DraftSnapshot draft;

    @JdbcTypeCode(SqlTypes.JSON)
    private PriceScheduleSnapshot prices;

    ProductCompletenessEntity(String productId) {
        this.productId = productId;
    }
}
