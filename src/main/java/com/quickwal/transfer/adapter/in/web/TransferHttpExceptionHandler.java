package com.quickwal.transfer.adapter.in.web;

import com.quickwal.transfer.adapter.in.web.dto.ApiErrorResponse;
import com.quickwal.transfer.core.domain.exception.IdempotencyConflictException;
import com.quickwal.transfer.core.domain.exception.OptimisticLockingException;
import com.quickwal.transfer.core.domain.exception.TransferNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;

/** Maps transfer use-case and request-decoding errors to HTTP responses. */
@RestControllerAdvice(assignableTypes = TransferController.class)
public class TransferHttpExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(TransferHttpExceptionHandler.class);

    /**
     * Converts malformed JSON, missing required headers, and invalid path values into HTTP 400.
     *
     * @param exception request decoding or binding error
     * @return a bad-request response body
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiErrorResponse> malformedRequest(Exception exception) {
        log.info("Rejected malformed transfer API request: exceptionType={}",
                exception.getClass().getSimpleName());
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request is malformed or missing required fields");
    }

    /**
     * Converts invalid transfer input into HTTP 400.
     *
     * @param exception validation or parsing error
     * @return a bad-request response body
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> badRequest(IllegalArgumentException exception) {
        log.info("Rejected invalid transfer API request: reason={}", exception.getMessage());
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage());
    }

    /**
     * Converts a transfer lookup miss into HTTP 404.
     *
     * @param exception missing-transfer error
     * @return a not-found response body
     */
    @ExceptionHandler(TransferNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> notFound(TransferNotFoundException exception) {
        log.info("Transfer API lookup failed: {}", exception.getMessage());
        return error(HttpStatus.NOT_FOUND, "TRANSFER_NOT_FOUND", exception.getMessage());
    }

    /**
     * Converts a reused idempotency key with a different request into HTTP 409.
     *
     * @param exception idempotency conflict
     * @return a conflict response body
     */
    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiErrorResponse> idempotencyConflict(IdempotencyConflictException exception) {
        log.info("Transfer API idempotency conflict: {}", exception.getMessage());
        return error(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", exception.getMessage());
    }

    /**
     * Converts a concurrent transfer-state update into HTTP 409.
     *
     * @param exception optimistic-locking conflict
     * @return a conflict response body
     */
    @ExceptionHandler(OptimisticLockingException.class)
    public ResponseEntity<ApiErrorResponse> optimisticLockingConflict(OptimisticLockingException exception) {
        log.info("Transfer API state conflict: {}", exception.getMessage());
        return error(HttpStatus.CONFLICT, "TRANSFER_CONFLICT", exception.getMessage());
    }

    /**
     * Logs an unexpected controller failure and returns a sanitized server error.
     *
     * @param exception unexpected exception raised while handling a transfer request
     * @return generic internal-server-error response
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> unexpected(Exception exception) {
        String traceId = UUID.randomUUID().toString();
        log.error("Unexpected transfer API failure: traceId={}", traceId, exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse("INTERNAL_ERROR", "An unexpected error occurred", traceId));
    }

    /**
     * Builds the standard transfer API error response.
     *
     * @param status HTTP status to return
     * @param code stable API error code
     * @param message human-readable error detail
     * @return status and error body
     */
    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(code, message, null));
    }
}
