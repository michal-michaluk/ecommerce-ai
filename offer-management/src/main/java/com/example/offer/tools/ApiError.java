package com.example.offer.tools;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The error body shared by every 4xx/5xx response (element 02, {@code ErrorBody}).
 * {@code details} carries {@code fields[]} for {@link ErrorCode#VALIDATION_FAILED} and
 * {@code blocking[]} for {@link ErrorCode#PUBLICATION_BLOCKED}; it is omitted otherwise.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(ErrorCode code, String message, Map<String, Object> details) {

    public ApiError {
        Objects.requireNonNull(code);
        Objects.requireNonNull(message);
        details = details == null ? null : Map.copyOf(details);
    }

    public static ApiError of(ErrorCode code, String message) {
        return new ApiError(code, message, null);
    }

    /** {@code 401 UNAUTHENTICATED}: the request carries no valid bearer token. */
    public static ApiError unauthenticated() {
        return of(ErrorCode.UNAUTHENTICATED, "Authentication is required.");
    }

    /** {@code 403 FORBIDDEN}: the authenticated role does not reach the endpoint. */
    public static ApiError forbidden() {
        return of(ErrorCode.FORBIDDEN, "The role is not allowed to perform this operation.");
    }

    /** {@code 422 VALIDATION_FAILED} naming the offending request fields. */
    public static ApiError validation(List<String> fields) {
        return new ApiError(ErrorCode.VALIDATION_FAILED, "Request validation failed.",
                Map.of("fields", fields));
    }

    /** {@code 422 PUBLICATION_BLOCKED} carrying the open gate items. */
    public static ApiError publicationBlocked(List<BlockingItem> blocking) {
        return new ApiError(ErrorCode.PUBLICATION_BLOCKED,
                "The description cannot be published while the quality gate reports items.",
                Map.of("blocking", blocking));
    }

    /** One open gate item of {@code PUBLICATION_BLOCKED.details.blocking[]}. */
    public record BlockingItem(String code, String label) {
        public BlockingItem {
            Objects.requireNonNull(code);
            Objects.requireNonNull(label);
        }
    }
}
