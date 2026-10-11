package com.navan.billing.transaction.identity;

/** Outcome of trying to claim an {@code external_id}. */
public sealed interface IdentityClaim {

    /** This request won: a new header row exists with {@code id}. Caller continues writing child rows. */
    record Claimed(long id) implements IdentityClaim {
    }

    /** Someone (maybe a concurrent request) already owns the external_id. Nothing was written. */
    record AlreadyExists(long existingId) implements IdentityClaim {
    }
}
