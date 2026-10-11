package com.navan.billing.common.error;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.navan.billing.transaction.dto.metadata.TransactionMetadata;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> api(ApiException e) {
        String existingId = e instanceof ConflictException c ? c.existingId() : null;
        return ResponseEntity.status(e.status()).body(new ErrorResponse(e.code(), e.getMessage(), existingId));
    }

    /**
     * Malformed JSON, unknown {@code type}, unknown enum such as {@code fee_kind}, wrong value types. Worded by
     * {@link JsonErrors}; Jackson's own message is never echoed because it names internal classes.
     *
     * <p>Jackson drops the {@code metadata} segment from the path of values bound through the sibling {@code type}
     * (EXTERNAL_PROPERTY), so a path that starts inside a metadata subtype gets it back.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException e) {
        String detail = "body is missing or not JSON";
        if (e.getCause() instanceof JsonProcessingException jackson) {
            boolean insideMetadata = jackson instanceof JsonMappingException mapping
                    && JsonErrors.startsInside(mapping, TransactionMetadata.class);
            detail = JsonErrors.describe(jackson, insideMetadata ? "metadata" : "");
        }
        return ResponseEntity.badRequest().body(ErrorResponse.of("bad_request", "invalid request body: " + detail));
    }

    /** Bean Validation failures on @Valid request bodies, reported with the JSON (snake_case) field names. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> invalid(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> JsonErrors.toJsonPath(f.getField()) + " " + f.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(ErrorResponse.of("validation_failed", detail));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> typeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("bad_request", "invalid parameter: " + e.getName()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception e) {
        log.error("unhandled error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("internal_error", "unexpected error"));
    }
}
