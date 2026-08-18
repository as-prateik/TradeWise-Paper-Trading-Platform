package com.tradewise.common;

import java.math.RoundingMode;

/**
 * Single source of truth for money handling.
 *
 * <p>Storage scale is 4 (NUMERIC(19,4) in PostgreSQL). Every amount is rescaled with
 * {@link #ROUNDING} before being persisted. Display rounding to 2 decimals is the
 * client's responsibility. BigDecimal values must never be constructed from double.
 */
public final class MoneyConstants {

    public static final int MONEY_SCALE = 4;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private MoneyConstants() {
    }
}
