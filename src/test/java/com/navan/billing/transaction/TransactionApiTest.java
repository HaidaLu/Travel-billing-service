package com.navan.billing.transaction;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.navan.billing.support.ApiTestBase;

/** Required tests from the brief; the number in each name matches the brief's numbered list. */
class TransactionApiTest extends ApiTestBase {

    /**
     * TODO: POST flight, hotel, rail, trip_fee, agent_call_fee from Fixtures.seed(...). Each is 201 with an id.
     * Then GET each one and assert real structure comes back: coupons[0].flight_number, hotel address.city,
     * two rail legs, fee_kind. Not just "metadata is non-null".
     */
    @Test
    @Disabled("TODO")
    void t01_createsEveryTypeAndRoundTripsMetadata() {
    }

    /**
     * TODO: POST flight, then POST duplicate_flight (same external_id). Assert the chosen status (409 or 200),
     * that the body carries the FIRST id, and that the list shows exactly one row with that external_id.
     */
    @Test
    @Disabled("TODO")
    void t02_duplicateExternalIdDoesNotInsertSecondRow() {
    }

    /**
     * TODO: create txn → invoice it → PATCH total. Expect 409; GET txn shows the old total;
     * GET invoice totals unchanged.
     */
    @Test
    @Disabled("TODO")
    void t07_patchInvoicedTransactionIs409() {
    }

    /** TODO: create txn → invoice it → DELETE txn. Expect 409; GET txn is still 200. */
    @Test
    @Disabled("TODO")
    void t08_deleteInvoicedTransactionIs409() {
    }

    /**
     * TODO: create &gt; 50 txns. GET /transactions returns exactly 50 plus a next cursor;
     * limit=1000 → 400 (per PageRequest policy). Following the cursor yields the rest with no overlap.
     */
    @Test
    @Disabled("TODO")
    void t10_listIsBoundedAndPaginated() {
    }

    // Worth adding beyond the minimum: unknown type → 400, unknown fee_kind → 400, PATCH happy path,
    // DELETE happy path (child rows gone), GET unknown id → 404, amounts with 3 decimals → 400.
}
