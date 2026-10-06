package com.example.offer.draft;

import com.example.offer.auth.Audit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.function.Consumer;

@Service
@Transactional
@RequiredArgsConstructor
public class DraftService {

    private final DraftRepository repository;

    public Optional<DraftSnapshot> get(String productId) {
        return repository.get(productId).map(DescriptionDraft::toDraftSnapshot);
    }

    public DraftSnapshot create(String productId, String version, Title title, Audit audit) {
        DescriptionDraft draft = DescriptionDraft.newDraft(productId, version, title, audit);
        repository.save(draft);
        return draft.toDraftSnapshot();
    }

    public Optional<DraftSnapshot> edit(String productId, UpdateDraft update, Audit audit) {
        return mutate(productId, draft -> draft.edit(update, audit));
    }

    public Optional<DraftSnapshot> attachPhoto(String productId, Photo photo, Audit audit) {
        return mutate(productId, draft -> draft.attachPhoto(photo, audit));
    }

    public Optional<DraftSnapshot> detachPhoto(String productId, String photoId, Audit audit) {
        return mutate(productId, draft -> draft.detachPhoto(photoId, audit));
    }

    public Optional<DraftSnapshot> requestReview(String productId, ReviewRequest request, Audit audit) {
        return mutate(productId, draft -> draft.requestReview(request, audit));
    }

    public Optional<DraftSnapshot> approve(String productId, ReviewRequest decision, Audit audit) {
        return mutate(productId, draft -> draft.approve(decision, audit));
    }

    public Optional<DraftSnapshot> reject(String productId, ReviewRequest decision, String reason, Audit audit) {
        return mutate(productId, draft -> draft.reject(decision, reason, audit));
    }

    private Optional<DraftSnapshot> mutate(String productId, Consumer<DescriptionDraft> operation) {
        return repository.get(productId).map(draft -> {
            operation.accept(draft);
            repository.save(draft);
            return draft.toDraftSnapshot();
        });
    }
}
