package com.tradewise.marketdata.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A point-in-time price snapshot. Quote prices use scale 2 (exchange tick size);
 * downstream money arithmetic widens to the storage scale of 4.
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
        Instant asOf
) {
}
