package com.navan.billing.transaction.dto;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TransactionType {
    FLIGHT("flight"),
    HOTEL("hotel"),
    RAIL("rail"),
    NAVAN_FEE("navan_fee");

    private final String wire;

    TransactionType(String wire) {
        this.wire = wire;
    }

    /** Wire / DB value. Jackson also uses this for deserialization, so unknown values fail with 400. */
    @JsonValue
    public String wire() {
        return wire;
    }

    public static TransactionType fromWire(String value) {
        for (TransactionType t : values()) {
            if (t.wire.equals(value)) {
                return t;
            }
        }
        throw new IllegalArgumentException("unknown transaction type: " + value);
    }
}
