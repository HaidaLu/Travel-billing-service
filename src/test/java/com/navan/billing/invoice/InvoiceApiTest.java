package com.navan.billing.invoice;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.navan.billing.support.ApiTestBase;

class InvoiceApiTest extends ApiTestBase {

    /**
     * TODO: create the 5 trip seeds, POST /invoices with all ids. Assert grand total 685.70 EUR, per-type
     * contributions, tax rollup and fee rollup against Fixtures.expectedInvoice(). Compare money as BigDecimal
     * with compareTo, never as double.
     */
    @Test
    @Disabled("TODO")
    void t03_tripInvoiceMatchesExpectedTotals() {
    }

    /**
     * TODO: invoice the flight, then change the stored flight total. The PATCH API returns 409 once invoiced
     * (by design), so how else can the stored row change? Options: update the row directly via JdbcClient in the
     * test, or PATCH a second, un-invoiced flight. Then GET the invoice and assert the flight contribution is still 220.00.
     */
    @Test
    @Disabled("TODO")
    void t04_invoiceIsASnapshot() {
    }

    /** TODO: one EUR txn + one USD txn (edit a seed's currency and external_id) → POST /invoices is 409, no invoice created. */
    @Test
    @Disabled("TODO")
    void t05_mixedCurrencyIs409() {
    }

    /** TODO: invoice two txns → DELETE invoice is 204 → GET invoice is 404 → both txns still GET 200 (and are now patchable). */
    @Test
    @Disabled("TODO")
    void t09_deleteInvoiceKeepsTransactions() {
    }

    // Worth adding: same id twice → chosen policy; missing id → 404 and nothing written; PATCH invoice rebuilds
    // from current data; PATCH with mixed currency leaves the invoice unchanged.
}
