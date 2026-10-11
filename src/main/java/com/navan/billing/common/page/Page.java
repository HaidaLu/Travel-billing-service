package com.navan.billing.common.page;

import java.util.List;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/** One page of results. {@code nextCursor} is null on the last page. */
public record Page<T>(List<T> items, String nextCursor) {

    /**
     * Builds a page from a query that fetched up to {@code limit + 1} rows. The extra row is never returned; it only
     * proves a next page exists, and the cursor points after the last row that IS returned.
     */
    public static <T> Page<T> fromLookahead(List<T> rows, int limit, ToLongFunction<T> rowId) {
        if (rows.size() <= limit) {
            return new Page<>(rows, null);
        }
        List<T> items = List.copyOf(rows.subList(0, limit));
        return new Page<>(items, Cursor.encode(rowId.applyAsLong(items.get(limit - 1))));
    }

    public <R> Page<R> map(Function<T, R> fn) {
        return new Page<>(items.stream().map(fn).toList(), nextCursor);
    }
}
