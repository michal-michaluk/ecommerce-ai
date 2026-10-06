package com.example.offer.pricing;

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

import java.util.List;

/** Maps the pricing context's typed failures onto the element-02 error contract. */
@RestControllerAdvice(basePackages = "com.example.offer.pricing")
@Order(Ordered.HIGHEST_PRECEDENCE)
class PricingAdvice {

    @ExceptionHandler(PriceOverlap.class)
    ResponseEntity<ApiError> onPriceOverlap(PriceOverlap ignored) {
        return ApiErrors.of(ErrorCode.PRICE_OVERLAP);
    }

    @ExceptionHandler(InvalidDateRange.class)
    ResponseEntity<ApiError> onInvalidDateRange(InvalidDateRange ignored) {
        return ApiErrors.of(ErrorCode.INVALID_DATE_RANGE);
    }

    /** No price-specific "not editable" code exists yet; the state conflict keeps the shared 409 code. */
    @ExceptionHandler(EntryNotEditable.class)
    ResponseEntity<ApiError> onEntryNotEditable(EntryNotEditable ignored) {
        return ApiErrors.of(ErrorCode.DRAFT_NOT_EDITABLE,
                "Only a scheduled price entry can be changed or deleted.");
    }

    @ExceptionHandler(DecisionDenied.class)
    ResponseEntity<ApiError> onDecisionDenied(DecisionDenied denied) {
        return ApiErrors.of(denied.code());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> onIllegalArgument(IllegalArgumentException ignored) {
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
                .body(ApiError.validation(List.of()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> onAccessDenied(AccessDeniedException ignored) {
        return ApiErrors.of(ErrorCode.FORBIDDEN);
    }
}
