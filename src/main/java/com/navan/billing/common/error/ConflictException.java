package com.navan.billing.common.error;

import org.springframework.http.HttpStatus;

/**
 * 409. {@code existingId} is set for duplicate-identity conflicts so the client can find the stored row.
 * Use the static factories so each conflict reason has a stable machine-readable {@code code}.
 */
public class ConflictException extends ApiException {

    private final String existingId;

    private ConflictException(String code, String message, String existingId) {
        super(HttpStatus.CONFLICT, code, message);
        this.existingId = existingId;
    }

    public static ConflictException duplicateExternalId(String externalId, String existingId) {
        return new ConflictException("duplicate_external_id",
                "transaction with external_id '" + externalId + "' already exists", existingId);
    }

    public static ConflictException transactionInvoiced(String transactionId) {
        return new ConflictException("transaction_invoiced",
                "transaction " + transactionId + " is on an invoice and is frozen", null);
    }

    public static ConflictException mixedCurrency(String message) {
        return new ConflictException("mixed_currency", message, null);
    }

    public static ConflictException duplicateInvoiceLine(String transactionId) {
        return new ConflictException("duplicate_invoice_line",
                "transaction " + transactionId + " listed more than once", null);
    }

    public String existingId() {
        return existingId;
    }
}
