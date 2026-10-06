package com.example.offer.tools;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Objects;

/**
 * Maps the platform-level failures of the generated app onto the element-02 error contract.
 * Bounded contexts map their own domain failures (typed exceptions, per-context advice) onto
 * {@link ErrorCode} + {@link ApiError}; this advice never maps exception messages into a
 * response and honours {@code server.error.include-message: never}.
 */
@RestControllerAdvice
public class ApiErrorAdvice {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> onBodyValidation(MethodArgumentNotValidException ex) {
        List<String> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getField)
                .distinct()
                .toList();
        return unprocessable(ApiError.validation(fields));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> onMethodValidation(HandlerMethodValidationException ex) {
        List<String> fields = ex.getParameterValidationResults().stream()
                .map(result -> result.getMethodParameter().getParameterName())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return unprocessable(ApiError.validation(fields));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> onConstraintViolation(ConstraintViolationException ex) {
        List<String> fields = ex.getConstraintViolations().stream()
                .map(ApiErrorAdvice::lastNode)
                .distinct()
                .toList();
        return unprocessable(ApiError.validation(fields));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> onUnmatchedRoute(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(ErrorCode.NOT_FOUND, "No resource matches the request."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> onUnexpected(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse) {
            return contractBody(errorResponse);
        }
        return internalError();
    }

    private static ResponseEntity<ApiError> unprocessable(ApiError error) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(error);
    }

    private static ResponseEntity<ApiError> internalError() {
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status())
                .body(ApiError.of(ErrorCode.INTERNAL_ERROR, messageFor(ErrorCode.INTERNAL_ERROR)));
    }

    /**
     * An {@link ErrorResponse} maps to a contract body only when its status has a canonical
     * element-02 code; any other status keeps its own code-less body so the transport status
     * never disagrees with {@code code}.
     */
    private static ResponseEntity<ApiError> contractBody(ErrorResponse error) {
        HttpStatusCode status = error.getStatusCode();
        ErrorCode code = switch (status.value()) {
            case 401 -> ErrorCode.UNAUTHENTICATED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            case 422 -> ErrorCode.VALIDATION_FAILED;
            case 500 -> ErrorCode.INTERNAL_ERROR;
            default -> null;
        };
        if (code == null) {
            return ResponseEntity.status(status).build();
        }
        return ResponseEntity.status(status).body(ApiError.of(code, messageFor(code)));
    }

    private static String messageFor(ErrorCode code) {
        return switch (code) {
            case UNAUTHENTICATED -> "Authentication is required.";
            case FORBIDDEN -> "The role is not allowed to perform this operation.";
            case NOT_FOUND -> "No resource matches the request.";
            default -> "Unexpected error.";
        };
    }

    private static String lastNode(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int dot = path.lastIndexOf('.');
        return dot < 0 ? path : path.substring(dot + 1);
    }
}
