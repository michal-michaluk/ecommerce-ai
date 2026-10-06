package com.example.offer.catalog;

import com.example.offer.tools.ApiError;
import com.example.offer.tools.ErrorCode;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps the catalog's typed failures onto the element-02 error contract. It is ordered ahead of the
 * platform-wide catch-all so a not-found never degrades to {@code 500}; the body carries only a
 * canonical code and a fixed message, never {@code getMessage()}.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class CatalogExceptionAdvice {

    @ExceptionHandler(ProductReadNotFound.class)
    ResponseEntity<ApiError> onProductNotFound(ProductReadNotFound ex) {
        return ResponseEntity.status(ErrorCode.PRODUCT_NOT_FOUND.status())
                .body(ApiError.of(ErrorCode.PRODUCT_NOT_FOUND,
                        "Product " + ex.productId() + " does not exist."));
    }

    @ExceptionHandler(ReviewRequestNotFound.class)
    ResponseEntity<ApiError> onReviewNotFound(ReviewRequestNotFound ex) {
        return ResponseEntity.status(ErrorCode.REVIEW_NOT_FOUND.status())
                .body(ApiError.of(ErrorCode.REVIEW_NOT_FOUND,
                        "Review request " + ex.reviewRequestId() + " does not exist."));
    }
}
