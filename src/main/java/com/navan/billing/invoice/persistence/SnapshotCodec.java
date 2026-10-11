package com.navan.billing.invoice.persistence;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.navan.billing.invoice.domain.TransactionSnapshot;

/** (De)serializes {@link TransactionSnapshot} to/from invoice_lines.snapshot_json. */
@Component
class SnapshotCodec {

    private final ObjectMapper objectMapper;

    SnapshotCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String encode(TransactionSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot encode snapshot of " + snapshot.transactionId(), e);
        }
    }

    TransactionSnapshot decode(String json) {
        try {
            return objectMapper.readValue(json, TransactionSnapshot.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("corrupt invoice snapshot: " + json, e);
        }
    }
}
