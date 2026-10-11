package com.navan.billing.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.navan.billing.common.error.BadRequestException;

/**
 * Money is {@link BigDecimal} in the domain and {@code long} minor units at a fixed scale of 2 in the DB.
 * Never {@code double}. Input with more than 2 decimal places is rejected rather than silently rounded.
 */
public final class MinorUnits {

    public static final int SCALE = 2;

    private MinorUnits() {
    }

    public static long toMinor(BigDecimal amount) {
        try {
            return amount.setScale(SCALE, RoundingMode.UNNECESSARY).unscaledValue().longValueExact();
        } catch (ArithmeticException e) {
            throw new BadRequestException("amount " + amount.toPlainString() + " has more than " + SCALE + " decimals");
        }
    }

    public static BigDecimal fromMinor(long minor) {
        return BigDecimal.valueOf(minor, SCALE);
    }
}
