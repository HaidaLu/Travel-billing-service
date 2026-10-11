package com.navan.billing.invoice.persistence;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.navan.billing.invoice.domain.TransactionSnapshot;

/** (De)serializes {@link TransactionSnapshot} to/from invoice_lines.snapshot_json. */
@Component
class SnapshotCodec {

    private final ObjectMapper objectMapper;

    SnapshotCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * TODO: JSON for storage. Must round-trip exactly: decode(encode(s)).equals(s).
     *  - Gotcha: with EXTERNAL_PROPERTY + visible=true, check the output doesn't contain "type" twice.
     *  - Money must stay exact (BigDecimal written as JSON number or string, never double).
     */
    String encode(TransactionSnapshot snapshot) {
        throw new UnsupportedOperationException("TODO");
    }

    /** TODO: inverse of encode. */
    TransactionSnapshot decode(String json) {
        throw new UnsupportedOperationException("TODO");
    }
}
