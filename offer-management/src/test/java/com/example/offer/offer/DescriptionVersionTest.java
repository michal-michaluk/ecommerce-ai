package com.example.offer.offer;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.example.offer.offer.OfferFixture.PRODUCT_ID;
import static com.example.offer.offer.OfferFixture.REVIEWER;
import static com.example.offer.offer.OfferFixture.AT;
import static com.example.offer.offer.OfferFixture.version;
import static org.assertj.core.api.Assertions.assertThat;

class DescriptionVersionTest {

    @Test
    void aVersionIsImmutableAndCopiesItsContents() {                  // RULE-9
        List<String> mutablePhotoIds = new ArrayList<>(List.of("ph-1"));
        DescriptionVersion version = new DescriptionVersion(PRODUCT_ID, "v1", "Kosiarka",
                "Opis", Map.of("category", "Ogród"), mutablePhotoIds, null, REVIEWER, AT);

        mutablePhotoIds.add("ph-2");

        assertThat(version.photoIds()).containsExactly("ph-1");
    }

    @Test
    void aRevertedVersionPointsAtItsBasisWithoutRewritingIt() {       // RULE-12
        DescriptionVersion original = version("v2");
        DescriptionVersion reverted = version("v3", "v2");

        assertThat(reverted.basedOnVersion()).isEqualTo("v2");
        assertThat(reverted.isReverted()).isTrue();
        assertThat(original.basedOnVersion()).isNull();
        assertThat(original.isReverted()).isFalse();
    }

    @Test
    void aVersionReferencesPhotoIdentitiesItDoesNotOwn() {           // RULE-13
        DescriptionVersion version = new DescriptionVersion(PRODUCT_ID, "v1", "Kosiarka",
                "Opis", Map.of("category", "Ogród"), List.of("ph-1", "ph-2"), null, REVIEWER,
                Instant.parse("2019-05-01T09:00:00Z"));

        assertThat(version.photoIds()).containsExactly("ph-1", "ph-2");
    }
}
