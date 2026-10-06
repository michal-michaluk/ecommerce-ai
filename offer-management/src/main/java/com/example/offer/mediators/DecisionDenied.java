package com.example.offer.mediators;

import com.example.offer.offer.Completeness;
import com.example.offer.tools.ErrorCode;

import java.util.List;
import java.util.Objects;

/** Raised when a decision policy denies an orchestrated flow; carries its element-02 code (RULE-58). */
public class DecisionDenied extends RuntimeException {

    private final ErrorCode code;
    private final List<Completeness.MissingRequirement> blocking;

    public DecisionDenied(ErrorCode code) {
        this(code, List.of());
    }

    public DecisionDenied(ErrorCode code, List<Completeness.MissingRequirement> blocking) {
        super("decision denied: " + code);
        this.code = Objects.requireNonNull(code, "code is required");
        this.blocking = List.copyOf(blocking);
    }

    public ErrorCode code() {
        return code;
    }

    /** The open gate items of a {@code PUBLICATION_BLOCKED} denial, empty otherwise. */
    public List<Completeness.MissingRequirement> blocking() {
        return blocking;
    }
}
