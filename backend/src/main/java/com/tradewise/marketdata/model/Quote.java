package com.tradewise.marketdata.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A price snapshot.
 *
 * <p>{@code tradingDay} and {@code source} exist so the client can state plainly what
 * it is showing: with the free upstream tier the price is a real <em>closing</em>
 * price for a given day, not a live tick, and when no live data is available it is
 * simulated. Hiding that distinction is what made prices look simply wrong.
 */
public record Quote(
        String symbol,
        String companyName,
        BigDecimal price,
        BigDecimal dayOpen,
        BigDecimal dayHigh,
        BigDecimal dayLow,
        BigDecimal previousClose,
        BigDecimal changeAbsolute,
        BigDecimal changePercent,
        long volume,
        LocalDate tradingDay,
        DataSource source,
        Instant asOf
) {
}
