package com.example.offer.draft;

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

/** Maps the draft context's typed failures onto the element-02 error contract. */
@RestControllerAdvice(basePackages = "com.example.offer.draft")
@Order(Ordered.HIGHEST_PRECEDENCE)
class DraftAdvice {

    @ExceptionHandler(DraftNotEditable.class)
    ResponseEntity<ApiError> onDraftNotEditable(DraftNotEditable ignored) {
        return ApiErrors.of(ErrorCode.DRAFT_NOT_EDITABLE);
    }

    @ExceptionHandler(ReviewNotPending.class)
    ResponseEntity<ApiError> onReviewNotPending(ReviewNotPending ignored) {
        return ApiErrors.of(ErrorCode.REVIEW_NOT_PENDING);
    }

    @ExceptionHandler(ReviewerIsAuthor.class)
    ResponseEntity<ApiError> onReviewerIsAuthor(ReviewerIsAuthor ignored) {
        return ApiErrors.of(ErrorCode.REVIEWER_IS_AUTHOR);
    }

    @ExceptionHandler(PhotoFormatUnsupported.class)
    ResponseEntity<ApiError> onPhotoFormatUnsupported(PhotoFormatUnsupported ignored) {
        return ApiErrors.of(ErrorCode.PHOTO_FORMAT_UNSUPPORTED);
    }

    @ExceptionHandler(PhotoTooSmall.class)
    ResponseEntity<ApiError> onPhotoTooSmall(PhotoTooSmall ignored) {
        return ApiErrors.of(ErrorCode.PHOTO_TOO_SMALL);
    }

    @ExceptionHandler(PhotoTooLarge.class)
    ResponseEntity<ApiError> onPhotoTooLarge(PhotoTooLarge ignored) {
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
                .body(ApiError.validation(List.of("file")));
    }


    @ExceptionHandler(DecisionDenied.class)
    ResponseEntity<ApiError> onDecisionDenied(DecisionDenied denied) {
        return ApiErrors.of(denied.code());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> onAccessDenied(AccessDeniedException ignored) {
        return ApiErrors.of(ErrorCode.FORBIDDEN);
    }
}
