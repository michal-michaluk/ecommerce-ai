package com.example.offer.mediators;

import com.example.offer.tools.ErrorCode;

import java.util.Objects;

/** Raised when a decision policy denies an orchestrated flow; carries its element-02 code (RULE-58). */
public class DecisionDenied extends RuntimeException {

    private final ErrorCode code;

    public DecisionDenied(ErrorCode code) {
        super("decision denied: " + code);
        this.code = Objects.requireNonNull(code, "code is required");
    }

    public ErrorCode code() {
        return code;
    }
}
