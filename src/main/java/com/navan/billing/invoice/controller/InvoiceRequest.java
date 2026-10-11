package com.navan.billing.invoice.controller;

import java.util.List;

import com.navan.billing.common.error.BadRequestException;
import com.navan.billing.common.id.PublicId;

/** Body of POST / PATCH /invoices. Accepts {@code transaction_id} or {@code transaction_ids}, not both. */
public record InvoiceRequest(String transactionId, List<String> transactionIds) {

    /** Decoded rowids in request order (duplicates preserved; the service applies the duplicate policy). */
    public List<Long> rowIds() {
        if ((transactionId == null) == (transactionIds == null)) {
            throw new BadRequestException("provide exactly one of transaction_id or transaction_ids");
        }
        List<String> ids = transactionId != null ? List.of(transactionId) : transactionIds;
        if (ids.isEmpty()) {
            throw new BadRequestException("transaction_ids must not be empty");
        }
        return ids.stream().map(PublicId.TRANSACTION::decode).toList();
    }
}
