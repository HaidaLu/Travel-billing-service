package com.navan.billing.common.page;

import com.navan.billing.common.error.BadRequestException;

/**
 * Keyset pagination: {@code WHERE id > :afterId ORDER BY id LIMIT :limit + 1}.
 * The extra row tells us whether there is a next page without a COUNT(*).
 *
 * @param afterId rowid of the last item on the previous page (0 for the first page)
 */
public record PageRequest(int limit, long afterId) {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;

    /** Policy: limit &gt; 100 is a 400 (explicit) rather than a silent cap. Document in README. */
    public static PageRequest of(Integer limit, String cursor) {
        int l = limit == null ? DEFAULT_LIMIT : limit;
        if (l < 1 || l > MAX_LIMIT) {
            throw new BadRequestException("limit must be between 1 and " + MAX_LIMIT);
        }
        return new PageRequest(l, Cursor.decode(cursor));
    }
}
