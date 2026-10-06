package com.example.offer.draft;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.example.offer.draft.DraftFixture.PRODUCT_ID;
import static com.example.offer.draft.DraftFixture.VERSION;
import static com.example.offer.draft.DraftFixture.authorAudit;
import static com.example.offer.draft.DraftFixture.description;
import static com.example.offer.draft.DraftFixture.title;
import static org.assertj.core.api.Assertions.assertThat;

class DraftServiceTest {

    private final Map<String, DescriptionDraft> store = new HashMap<>();
    private final DraftService service = new DraftService(new FakeRepository());

    @Test
    void createThenGet() {
        service.create(PRODUCT_ID, VERSION, title(), authorAudit());

        DraftSnapshot snapshot = service.get(PRODUCT_ID).orElseThrow();
        assertThat(snapshot.state()).isEqualTo(DraftState.EDITING);
        assertThat(snapshot.revision()).isEqualTo(1);
    }

    @Test
    void editExistingDraft() {
        service.create(PRODUCT_ID, VERSION, title(), authorAudit());

        service.edit(PRODUCT_ID, UpdateDraft.builder().description(description()).build(), authorAudit());

        assertThat(service.get(PRODUCT_ID).orElseThrow().description()).isEqualTo(description());
    }

    @Test
    void missingProductIsEmpty() {
        assertThat(service.get("missing")).isEmpty();
        assertThat(service.edit("missing", UpdateDraft.builder().build(), authorAudit())).isEmpty();
    }

    private final class FakeRepository implements DraftRepository {

        @Override
        public Optional<DescriptionDraft> get(String productId) {
            return Optional.ofNullable(store.get(productId));
        }

        @Override
        public void save(DescriptionDraft draft) {
            store.put(draft.toDraftSnapshot().productId(), draft);
        }
    }
}
