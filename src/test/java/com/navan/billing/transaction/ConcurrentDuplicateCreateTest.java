package com.navan.billing.transaction;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.navan.billing.support.ApiTestBase;

/** Brief #6. A separate class (and DB file) so other tests' rows can't affect the count. */
class ConcurrentDuplicateCreateTest extends ApiTestBase {

    /**
     * TODO: fire N (e.g. 16) POSTs with the same external_id concurrently. Release them together with a latch or
     * barrier so they really overlap, then join.
     * Assert: exactly one 201; all others are the duplicate status and carry the SAME existing id; no 500s;
     * one row in the DB for that external_id.
     * Tip: also run it with the unique index temporarily removed and confirm the test FAILS. That proves the
     * test can catch the race.
     */
    @Test
    @Disabled("TODO")
    void t06_overlappingPostsLeaveExactlyOneRow() {
    }
}
