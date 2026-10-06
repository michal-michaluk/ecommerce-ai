package com.example.offer;

import com.example.offer.tools.ApiError;
import com.example.offer.tools.ErrorCode;
import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * Builds the element-02 error body for a typed failure without ever leaking
 * {@code exception.getMessage()} into the response. Shared by the per-context advices; the codes
 * and their messages are fixed here so every context renders the contract identically.
 */
public final class ApiErrors {

    private ApiErrors() {
    }

    public static ResponseEntity<ApiError> of(ErrorCode code) {
        return of(code, messageFor(code));
    }

    /** A context-specific message for a code whose shared wording would mislead (e.g. a price state conflict). */
    public static ResponseEntity<ApiError> of(ErrorCode code, String message) {
        ApiError body = code == ErrorCode.PUBLICATION_BLOCKED
                ? ApiError.publicationBlocked(List.of())
                : ApiError.of(code, message);
        return ResponseEntity.status(code.status()).body(body);
    }

    public static ResponseEntity<ApiError> of(ErrorCode code, List<ApiError.BlockingItem> blocking) {
        ApiError body = code == ErrorCode.PUBLICATION_BLOCKED
                ? ApiError.publicationBlocked(blocking)
                : ApiError.of(code, messageFor(code));
        return ResponseEntity.status(code.status()).body(body);
    }

    public static String messageFor(ErrorCode code) {
        return switch (code) {
            case UNAUTHENTICATED -> "Authentication is required.";
            case FORBIDDEN -> "The role is not allowed to perform this operation.";
            case NOT_FOUND -> "No resource matches the request.";
            case PRODUCT_NOT_FOUND -> "The product does not exist.";
            case REVIEW_NOT_FOUND -> "The review request does not exist.";
            case VERSION_NOT_FOUND -> "The version does not exist.";
            case REVIEWER_IS_AUTHOR -> "The reviewer is the author of the description.";
            case DRAFT_NOT_EDITABLE -> "The draft is not editable while a review is pending.";
            case REVIEW_ALREADY_PENDING -> "A review is already pending for this draft.";
            case REVIEW_NOT_PENDING -> "The review request is not pending.";
            case VERSION_NOT_APPROVED -> "The version has no approval.";
            case PRICE_OVERLAP -> "The price range overlaps an existing entry.";
            case PUBLICATION_BLOCKED -> "The description cannot be published while the quality gate reports items.";
            case PHOTO_FORMAT_UNSUPPORTED -> "The photo format is not supported.";
            case PHOTO_TOO_SMALL -> "The photo is below the minimum size.";
            case INVALID_DATE_RANGE -> "validTo is before validFrom.";
            case VALIDATION_FAILED -> "Request validation failed.";
            case INTERNAL_ERROR -> "Unexpected error.";
        };
    }
}
