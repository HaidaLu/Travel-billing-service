package com.navan.billing.invoice.snapshot;

import java.util.List;

import org.springframework.stereotype.Component;

import com.navan.billing.invoice.domain.InvoiceDraft;
import com.navan.billing.transaction.domain.Transaction;

/**
 * Snapshot/rollup seam: a pure function from stored transactions to an {@link InvoiceDraft}.
 * No DB, no HTTP, no clock, so it's unit-testable without Spring.
 */
@Component
public class InvoiceSnapshotBuilder {

    /**
     * TODO: build the draft.
     *  - Precondition: non-empty, already de-duplicated, all ids resolved (the service handles 404/duplicates).
     *  - All transactions share one currency, else ConflictException.mixedCurrency (409).
     *  - Each line is a deep copy (TransactionSnapshot.of), in request order.
     *  - total = Σ transaction totals (BigDecimal, exact).
     *  - tax rollup: group by (name, rate). Two VATs with different rates are two rows; a null rate is its own
     *    group. Fee rollup: group by name. Keep first-seen order so output is deterministic.
     *  - Question: does 0.07 equal 0.070 as a grouping key? (BigDecimal.equals vs compareTo.)
     *  - Expected for the trip fixture: total 685.70; taxes [Airport tax 15.00, VAT 0.07 21.70];
     *    fees [YQ 25.00, Booking fee 4.00].
     */
    public InvoiceDraft build(List<Transaction> transactions) {
        throw new UnsupportedOperationException("TODO");
    }
}
