package com.navan.billing.common.id;

import com.navan.billing.common.error.NotFoundException;

/**
 * Public ids are {@code <prefix>_<rowid>} (e.g. {@code txn_42}, {@code inv_7}); the DB keeps an INTEGER rowid.
 * Keeps the API opaque-looking and typed (a txn id can't be passed as an invoice id) while the PK stays cheap.
 */
public enum PublicId {
    TRANSACTION("txn"),
    INVOICE("inv");

    private final String prefix;

    PublicId(String prefix) {
        this.prefix = prefix + "_";
    }

    public String encode(long rowId) {
        return prefix + rowId;
    }

    /** A malformed or wrong-prefix id can never match a row, so it is reported as 404, not 400. */
    public long decode(String publicId) {
        if (publicId == null || !publicId.startsWith(prefix)) {
            throw new NotFoundException(name().toLowerCase() + " " + publicId + " not found");
        }
        try {
            return Long.parseLong(publicId.substring(prefix.length()));
        } catch (NumberFormatException e) {
            throw new NotFoundException(name().toLowerCase() + " " + publicId + " not found");
        }
    }
}
