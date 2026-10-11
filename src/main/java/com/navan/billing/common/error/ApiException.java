package com.navan.billing.common.error;

import org.springframework.http.HttpStatus;

/** Base for errors that map 1:1 to an HTTP status. Thrown from services, mapped by {@link GlobalExceptionHandler}. */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
