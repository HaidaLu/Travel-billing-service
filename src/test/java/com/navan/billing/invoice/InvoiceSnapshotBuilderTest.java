package com.navan.billing.invoice;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** Pure unit tests for the rollup logic. No Spring, no DB. */
class InvoiceSnapshotBuilderTest {

    /** TODO: same tax name with different rates → separate rollup rows; same (name, rate) → summed. */
    @Test
    @Disabled("TODO")
    void groupsTaxesByNameAndRate() {
    }

    /** TODO: fees group by name only; order is first-seen. */
    @Test
    @Disabled("TODO")
    void groupsFeesByName() {
    }

    /** TODO: totals are exact BigDecimal sums (e.g. 0.10 + 0.20 == 0.30). */
    @Test
    @Disabled("TODO")
    void totalsAreExact() {
    }

    /** TODO: mixed currencies → ConflictException. */
    @Test
    @Disabled("TODO")
    void rejectsMixedCurrency() {
    }
}
