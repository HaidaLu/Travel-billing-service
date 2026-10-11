package com.navan.billing.invoice.snapshot;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.navan.billing.common.error.ConflictException;
import com.navan.billing.common.id.PublicId;
import com.navan.billing.invoice.dto.FeeRollupLine;
import com.navan.billing.invoice.dto.InvoiceDraft;
import com.navan.billing.invoice.dto.TaxRollupLine;
import com.navan.billing.invoice.dto.TransactionSnapshot;
import com.navan.billing.transaction.dto.FeeLine;
import com.navan.billing.transaction.dto.TaxLine;
import com.navan.billing.transaction.dto.Transaction;

/**
 * Snapshot/rollup seam: a pure function from stored transactions to an {@link InvoiceDraft}.
 * No DB, no HTTP, no clock, so it's unit-testable without Spring.
 */
@Component
public class InvoiceSnapshotBuilder {

    /**
     * Precondition: non-empty, de-duplicated, every id resolved (the service owns 404 and duplicate policy).
     *
     * <ul>
     *   <li>All transactions must share one currency, else 409.</li>
     *   <li>Lines are copies ({@link TransactionSnapshot#of}) in request order; total is an exact BigDecimal sum.</li>
     *   <li>Tax rollup groups by (name, rate). Rates compare numerically (0.07 and 0.070 are one group); a missing
     *       rate is its own group. Fee rollup groups by name. Both keep first-seen order.</li>
     * </ul>
     */
    public InvoiceDraft build(List<Transaction> transactions) {
        if (transactions.isEmpty()) {
            throw new IllegalArgumentException("an invoice needs at least one transaction");
        }
        Set<String> currencies = transactions.stream().map(Transaction::currency)
                .collect(Collectors.toCollection(TreeSet::new));
        if (currencies.size() > 1) {
            throw ConflictException.mixedCurrency("transactions on one invoice must share a currency, got " + currencies);
        }

        List<TransactionSnapshot> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        Map<TaxKey, BigDecimal> taxes = new LinkedHashMap<>();
        Map<String, BigDecimal> fees = new LinkedHashMap<>();
        for (Transaction t : transactions) {
            lines.add(TransactionSnapshot.of(PublicId.TRANSACTION.encode(t.id()), t));
            total = total.add(t.total());
            for (TaxLine tax : t.taxLines()) {
                taxes.merge(new TaxKey(tax.name(), normalize(tax.rate())), tax.amount(), BigDecimal::add);
            }
            for (FeeLine fee : t.feeLines()) {
                fees.merge(fee.name(), fee.amount(), BigDecimal::add);
            }
        }

        List<TaxRollupLine> taxRollup = taxes.entrySet().stream()
                .map(e -> new TaxRollupLine(e.getKey().name(), e.getKey().rate(), e.getValue())).toList();
        List<FeeRollupLine> feeRollup = fees.entrySet().stream()
                .map(e -> new FeeRollupLine(e.getKey(), e.getValue())).toList();
        return new InvoiceDraft(currencies.iterator().next(), total, List.copyOf(lines), taxRollup, feeRollup);
    }

    /** BigDecimal.equals is scale-sensitive (0.07 ≠ 0.070), so rates are stripped before they become a map key. */
    private static BigDecimal normalize(BigDecimal rate) {
        return rate == null ? null : rate.stripTrailingZeros();
    }

    private record TaxKey(String name, BigDecimal rate) {
    }
}
