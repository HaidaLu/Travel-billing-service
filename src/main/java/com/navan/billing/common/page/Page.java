package com.navan.billing.common.page;

import java.util.List;
import java.util.function.Function;

/** One page of results. {@code nextCursor} is null on the last page. */
public record Page<T>(List<T> items, String nextCursor) {

    public <R> Page<R> map(Function<T, R> fn) {
        return new Page<>(items.stream().map(fn).toList(), nextCursor);
    }
}
