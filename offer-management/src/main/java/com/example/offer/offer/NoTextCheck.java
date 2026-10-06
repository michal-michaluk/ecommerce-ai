package com.example.offer.offer;

import com.example.offer.draft.DraftSnapshot;

import java.util.List;

/** Null-object default: with no checker configured the advisory list is empty (E05). */
public final class NoTextCheck implements TextCheck {

    @Override
    public List<TextIssue> issues(DraftSnapshot draft) {
        return List.of();
    }
}
