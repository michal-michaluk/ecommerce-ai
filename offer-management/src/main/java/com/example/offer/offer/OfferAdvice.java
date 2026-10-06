package com.example.offer.offer;

import com.example.offer.ApiErrors;
import com.example.offer.mediators.DecisionDenied;
import com.example.offer.tools.ApiError;
import com.example.offer.tools.ErrorCode;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps the offer context's typed failures onto the element-02 error contract. */
@RestControllerAdvice(basePackages = "com.example.offer.offer")
@Order(Ordered.HIGHEST_PRECEDENCE)
class OfferAdvice {

    @ExceptionHandler(ProductNotFound.class)
    ResponseEntity<ApiError> onProductNotFound(ProductNotFound ignored) {
        return ApiErrors.of(ErrorCode.PRODUCT_NOT_FOUND);
    }

    @ExceptionHandler(PublicationNotFound.class)
    ResponseEntity<ApiError> onPublicationNotFound(PublicationNotFound ignored) {
        return ApiErrors.of(ErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(PublicationNotCancellable.class)
    ResponseEntity<ApiError> onPublicationNotCancellable(PublicationNotCancellable ignored) {
        return ApiErrors.of(ErrorCode.VALIDATION_FAILED,
                "Only a scheduled publication can be cancelled.");
    }

    @ExceptionHandler(VersionNotFound.class)
    ResponseEntity<ApiError> onVersionNotFound(VersionNotFound ignored) {
        return ApiErrors.of(ErrorCode.VERSION_NOT_FOUND);
    }

    @ExceptionHandler(VersionNotApproved.class)
    ResponseEntity<ApiError> onVersionNotApproved(VersionNotApproved ignored) {
        return ApiErrors.of(ErrorCode.VERSION_NOT_APPROVED);
    }

    @ExceptionHandler(PublicationBlocked.class)
    ResponseEntity<ApiError> onPublicationBlocked(PublicationBlocked ignored) {
        return ApiErrors.of(ErrorCode.PUBLICATION_BLOCKED);
    }

    @ExceptionHandler(DecisionDenied.class)
    ResponseEntity<ApiError> onDecisionDenied(DecisionDenied denied) {
        return ApiErrors.of(denied.code(), denied.blocking().stream()
                .map(item -> new ApiError.BlockingItem(item.code(), item.label())).toList());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> onAccessDenied(AccessDeniedException ignored) {
        return ApiErrors.of(ErrorCode.FORBIDDEN);
    }
}
