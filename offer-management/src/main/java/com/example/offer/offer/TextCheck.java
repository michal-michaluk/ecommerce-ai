package com.example.offer.offer;

import com.example.offer.draft.DraftSnapshot;

import java.util.List;

/** Port for the optional advisory text check (Q27); its findings never block (RULE-44). */
public interface TextCheck {

    List<TextIssue> issues(DraftSnapshot draft);
}
