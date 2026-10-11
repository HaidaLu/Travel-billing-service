package com.navan.billing.common.page;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.navan.billing.common.error.BadRequestException;

/** Opaque cursor so clients don't build on the fact that it's a rowid today. */
public final class Cursor {

    private Cursor() {
    }

    public static String encode(long afterId) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(Long.toString(afterId).getBytes(StandardCharsets.UTF_8));
    }

    public static long decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("invalid cursor");
        }
    }
}
