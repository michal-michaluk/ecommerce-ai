package com.example.offer.draft;

import com.example.offer.auth.Audit;
import lombok.Builder;

@Builder
public record UpdateDraft(Title title, Description description, DraftAttributes attributes) {

    public void apply(DescriptionDraft draft, Audit audit) {
        draft.edit(this, audit);
    }
}
