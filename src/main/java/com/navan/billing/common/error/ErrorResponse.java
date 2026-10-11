package com.navan.billing.common.error;

/** Uniform error body. {@code existingId} only present on duplicate-identity 409s. */
public record ErrorResponse(String code, String message, String existingId) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, null);
    }
}
