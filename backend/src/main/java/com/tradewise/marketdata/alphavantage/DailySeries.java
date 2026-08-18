package com.tradewise.marketdata.alphavantage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A parsed TIME_SERIES_DAILY response: one upstream request yields both the latest
 * close and the historical bars, which is what keeps us inside 25 requests a day.
 *
 * @param bars ordered oldest-first
 */
public record DailySeries(String symbol, List<DailyBar> bars) {

    public record DailyBar(
            LocalDate tradingDay,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            long volume
    ) {
    }

    public DailyBar latest() {
        return bars.getLast();
    }

    /** The bar before the latest, used for the previous close / day change. */
    public DailyBar previous() {
        return bars.size() >= 2 ? bars.get(bars.size() - 2) : bars.getLast();
    }
}
