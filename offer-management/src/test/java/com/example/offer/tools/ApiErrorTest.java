package com.example.offer.tools;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorTest {

    @Test
    void everyContractCodeCarriesItsStatus() {
        assertThat(ErrorCode.VALIDATION_FAILED.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(ErrorCode.PRODUCT_NOT_FOUND.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.REVIEW_NOT_FOUND.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.VERSION_NOT_FOUND.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.UNAUTHENTICATED.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorCode.FORBIDDEN.status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ErrorCode.DRAFT_NOT_EDITABLE.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.REVIEW_ALREADY_PENDING.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.REVIEWER_IS_AUTHOR.status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ErrorCode.VERSION_NOT_APPROVED.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.PUBLICATION_BLOCKED.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(ErrorCode.PHOTO_FORMAT_UNSUPPORTED.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(ErrorCode.PHOTO_TOO_SMALL.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(ErrorCode.INVALID_DATE_RANGE.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(ErrorCode.REVIEW_NOT_PENDING.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.INTERNAL_ERROR.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void simpleErrorCarriesNoDetails() {
        ApiError error = ApiError.of(ErrorCode.PRODUCT_NOT_FOUND, "Product p-1 does not exist.");

        assertThat(error.code()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
        assertThat(error.message()).isEqualTo("Product p-1 does not exist.");
        assertThat(error.details()).isNull();
    }

    @Test
    void validationErrorNamesTheOffendingFields() {
        ApiError error = ApiError.validation(List.of("title"));

        assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(error.message()).isEqualTo("Request validation failed.");
        assertThat(error.details()).containsEntry("fields", List.of("title"));
    }

    @Test
    void publicationBlockedCarriesTheBlockingItems() {
        ApiError error = ApiError.publicationBlocked(
                List.of(new ApiError.BlockingItem("PRICE_REQUIRED", "Price valid for a date range")));

        assertThat(error.code()).isEqualTo(ErrorCode.PUBLICATION_BLOCKED);
        assertThat(error.details()).containsEntry("blocking", List.of(
                new ApiError.BlockingItem("PRICE_REQUIRED", "Price valid for a date range")));
    }
}
