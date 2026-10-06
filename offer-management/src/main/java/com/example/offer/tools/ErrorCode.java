package com.example.offer.tools;

import org.springframework.http.HttpStatus;

/**
 * Every error code of the frontend API contract (element 02), with the HTTP status the
 * contract assigns to it. Context-free shared kernel, so any bounded context can map its
 * own failures onto it — see {@link ApiErrorAdvice} for the platform-level mapping.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.UNPROCESSABLE_CONTENT),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND),
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND),
    VERSION_NOT_FOUND(HttpStatus.NOT_FOUND),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    REVIEWER_IS_AUTHOR(HttpStatus.FORBIDDEN),
    DRAFT_NOT_EDITABLE(HttpStatus.CONFLICT),
    DRAFT_NOT_FOUND(HttpStatus.CONFLICT),
    REVIEW_ALREADY_PENDING(HttpStatus.CONFLICT),
    REVIEW_NOT_PENDING(HttpStatus.CONFLICT),
    VERSION_NOT_APPROVED(HttpStatus.CONFLICT),
    PRICE_OVERLAP(HttpStatus.CONFLICT),
    PUBLICATION_BLOCKED(HttpStatus.UNPROCESSABLE_CONTENT),
    PHOTO_FORMAT_UNSUPPORTED(HttpStatus.UNPROCESSABLE_CONTENT),
    PHOTO_TOO_SMALL(HttpStatus.UNPROCESSABLE_CONTENT),
    INVALID_DATE_RANGE(HttpStatus.UNPROCESSABLE_CONTENT),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
