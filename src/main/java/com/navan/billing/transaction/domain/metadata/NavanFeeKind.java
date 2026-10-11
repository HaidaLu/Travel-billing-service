package com.navan.billing.transaction.domain.metadata;

import com.fasterxml.jackson.annotation.JsonValue;

/** Unknown kinds fail Jackson enum binding, which the global handler maps to 400. */
public enum NavanFeeKind {
    TRIP_FEE("trip_fee"),
    AGENT_CALL_FEE("agent_call_fee");

    private final String wire;

    NavanFeeKind(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    public static NavanFeeKind fromWire(String value) {
        for (NavanFeeKind k : values()) {
            if (k.wire.equals(value)) {
                return k;
            }
        }
        throw new IllegalArgumentException("unknown fee_kind: " + value);
    }
}
