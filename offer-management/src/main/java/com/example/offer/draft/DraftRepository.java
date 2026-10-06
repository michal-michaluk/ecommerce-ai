package com.example.offer.draft;

import java.util.Optional;

interface DraftRepository {
    Optional<DescriptionDraft> get(String productId);

    void save(DescriptionDraft draft);
}
