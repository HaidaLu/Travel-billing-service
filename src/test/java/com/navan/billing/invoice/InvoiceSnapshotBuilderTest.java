package com.navan.billing.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.navan.billing.common.error.ConflictException;
import com.navan.billing.invoice.dto.FeeRollupLine;
import com.navan.billing.invoice.dto.InvoiceDraft;
import com.navan.billing.invoice.dto.TaxRollupLine;
import com.navan.billing.invoice.dto.TransactionSnapshot;
import com.navan.billing.invoice.snapshot.InvoiceSnapshotBuilder;
import com.navan.billing.transaction.dto.FeeLine;
import com.navan.billing.transaction.dto.TaxLine;
import com.navan.billing.transaction.dto.Transaction;
import com.navan.billing.transaction.dto.TransactionType;
import com.navan.billing.transaction.dto.metadata.NavanFeeKind;
import com.navan.billing.transaction.dto.metadata.NavanFeeMetadata;

/** Pure unit tests for the rollup logic. No Spring, no DB. */
class InvoiceSnapshotBuilderTest {

    private static final Instant AT = Instant.parse("2026-03-11T10:00:00Z");

    private final InvoiceSnapshotBuilder builder = new InvoiceSnapshotBuilder();

    @Test
    void groupsTaxesByNameAndRate() {
        Transaction a = txn(1, "EUR", "10.00", List.of(
                tax("VAT", "0.07", "1.00"), tax("VAT", "0.19", "2.00"), tax("City tax", null, "3.00")), List.of());
        Transaction b = txn(2, "EUR", "10.00", List.of(
                tax("VAT", "0.070", "1.50"), tax("City tax", null, "0.50")), List.of());

        List<TaxRollupLine> rollup = builder.build(List.of(a, b)).taxRollup();

        assertThat(rollup).extracting(TaxRollupLine::name).containsExactly("VAT", "VAT", "City tax");
        assertThat(rollup.get(0).rate()).as("0.07 and 0.070 are one group").isEqualByComparingTo("0.07");
        assertThat(rollup.get(0).amount()).isEqualByComparingTo("2.50");
        assertThat(rollup.get(1).rate()).isEqualByComparingTo("0.19");
        assertThat(rollup.get(1).amount()).isEqualByComparingTo("2.00");
        assertThat(rollup.get(2).rate()).isNull();
        assertThat(rollup.get(2).amount()).isEqualByComparingTo("3.50");
    }

    @Test
    void groupsFeesByNameInFirstSeenOrder() {
        Transaction a = txn(1, "EUR", "10.00", List.of(), List.of(fee("YQ", "25.00"), fee("Booking fee", "4.00")));
        Transaction b = txn(2, "EUR", "10.00", List.of(), List.of(fee("YQ", "5.00")));

        List<FeeRollupLine> rollup = builder.build(List.of(a, b)).feeRollup();

        assertThat(rollup).extracting(FeeRollupLine::name).containsExactly("YQ", "Booking fee");
        assertThat(rollup.get(0).amount()).isEqualByComparingTo("30.00");
        assertThat(rollup.get(1).amount()).isEqualByComparingTo("4.00");
    }

    @Test
    void totalIsExactAndLinesAreSnapshotsInRequestOrder() {
        Transaction a = txn(7, "EUR", "0.10", List.of(), List.of());
        Transaction b = txn(3, "EUR", "0.20", List.of(), List.of());

        InvoiceDraft draft = builder.build(List.of(a, b));

        assertThat(draft.total()).as("0.10 + 0.20 with no float drift").isEqualByComparingTo("0.30");
        assertThat(draft.currency()).isEqualTo("EUR");
        assertThat(draft.lines()).extracting(TransactionSnapshot::transactionId).containsExactly("txn_7", "txn_3");
        assertThat(draft.lines().get(0).metadata()).isEqualTo(a.metadata());
    }

    @Test
    void rejectsMixedCurrency() {
        Transaction eur = txn(1, "EUR", "10.00", List.of(), List.of());
        Transaction usd = txn(2, "USD", "10.00", List.of(), List.of());

        assertThatThrownBy(() -> builder.build(List.of(eur, usd)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("EUR").hasMessageContaining("USD");
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private static Transaction txn(long id, String currency, String total, List<TaxLine> taxes, List<FeeLine> fees) {
        return new Transaction(id, TransactionType.NAVAN_FEE, "ext-" + id, currency, AT, new BigDecimal(total),
                taxes, fees, new NavanFeeMetadata(NavanFeeKind.TRIP_FEE, null, null), AT, AT);
    }

    private static TaxLine tax(String name, String rate, String amount) {
        return new TaxLine(name, rate == null ? null : new BigDecimal(rate), new BigDecimal(amount));
    }

    private static FeeLine fee(String name, String amount) {
        return new FeeLine(name, new BigDecimal(amount));
    }
}
