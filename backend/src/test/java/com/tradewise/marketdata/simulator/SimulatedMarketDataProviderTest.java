package com.tradewise.marketdata.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.tradewise.marketdata.model.Candle;
import com.tradewise.marketdata.model.HistoricalData;
import com.tradewise.marketdata.model.MarketStatus;
import com.tradewise.marketdata.model.Quote;
import com.tradewise.marketdata.model.TimeRange;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SimulatedMarketDataProviderTest {

    /** A fixed Tuesday 11:00 in New York (15:00 UTC), inside the regular session. */
    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-18T15:00:00Z");

    private SimulatedMarketDataProvider providerAt(Instant instant, boolean alwaysOpen) {
        return new SimulatedMarketDataProvider(
                Clock.fixed(instant, ZoneOffset.UTC), new SimulatorProperties(alwaysOpen));
    }

    @Test
    @DisplayName("the same instant always produces the same quote — the simulator is deterministic")
    void quotesAreDeterministic() {
        Quote first = providerAt(FIXED_INSTANT, true).getQuote("AAPL").orElseThrow();
        Quote second = providerAt(FIXED_INSTANT, true).getQuote("AAPL").orElseThrow();

        assertThat(first.price()).isEqualByComparingTo(second.price());
        assertThat(first.volume()).isEqualTo(second.volume());
        assertThat(first.dayHigh()).isEqualByComparingTo(second.dayHigh());
    }

    @Test
    @DisplayName("prices stay within the bounded envelope around the reference price")
    void pricesStayBounded() {
        var provider = providerAt(FIXED_INSTANT, true);
        var apple = StockUniverse.BY_SYMBOL.get("AAPL");
        BigDecimal reference = apple.referencePrice();

        for (int minutes = 0; minutes < 60 * 24 * 30; minutes += 17) {
            BigDecimal price = provider.priceAt(apple, FIXED_INSTANT.plusSeconds(minutes * 60L));
            assertThat(price).isPositive();
            // envelope: sum of component amplitudes = 6% around reference
            assertThat(price.doubleValue())
                    .isBetween(reference.doubleValue() * 0.93, reference.doubleValue() * 1.07);
        }
    }

    @Test
    @DisplayName("quote price and history closing price agree for the same instant")
    void historyIsConsistentWithQuotes() {
        var provider = providerAt(FIXED_INSTANT, true);

        Quote quote = provider.getQuote("MSFT").orElseThrow();
        HistoricalData history = provider.getHistory("MSFT", TimeRange.ONE_DAY).orElseThrow();
        Candle lastCandle = history.candles().getLast();

        // The last candle closes exactly at "now", computed by the same price function.
        assertThat(lastCandle.timestamp()).isEqualTo(FIXED_INSTANT);
        assertThat(lastCandle.close()).isEqualByComparingTo(quote.price());
        assertThat(history.candles()).hasSize(78);
    }

    @Test
    @DisplayName("candle invariants hold: low <= open,close <= high")
    void candleInvariantsHold() {
        HistoricalData history = providerAt(FIXED_INSTANT, true)
                .getHistory("NVDA", TimeRange.ONE_MONTH).orElseThrow();

        assertThat(history.candles()).isNotEmpty().allSatisfy(candle -> {
            assertThat(candle.high()).isGreaterThanOrEqualTo(candle.open());
            assertThat(candle.high()).isGreaterThanOrEqualTo(candle.close());
            assertThat(candle.low()).isLessThanOrEqualTo(candle.open());
            assertThat(candle.low()).isLessThanOrEqualTo(candle.close());
        });
    }

    @Test
    @DisplayName("unknown symbols yield empty, search matches symbol and company name")
    void unknownSymbolAndSearch() {
        var provider = providerAt(FIXED_INSTANT, true);

        assertThat(provider.getQuote("NOPE")).isEmpty();
        assertThat(provider.search("apple")).extracting("symbol").containsExactly("AAPL");
        assertThat(provider.search("inc")).extracting("symbol")
                .contains("AAPL", "AMZN", "TSLA", "V");
        assertThat(provider.search("   ")).isEmpty();
    }

    @Test
    @DisplayName("market status honors always-open, and otherwise follows US trading hours")
    void marketStatusFollowsConfigurationAndClock() {
        assertThat(providerAt(FIXED_INSTANT, true).getMarketStatus()).isEqualTo(MarketStatus.OPEN);

        // Tuesday 11:00 ET — regular session
        assertThat(providerAt(FIXED_INSTANT, false).getMarketStatus()).isEqualTo(MarketStatus.OPEN);
        // Tuesday 09:05 ET — pre-open
        assertThat(providerAt(Instant.parse("2026-08-18T13:05:00Z"), false).getMarketStatus())
                .isEqualTo(MarketStatus.PRE_OPEN);
        // Tuesday 17:00 ET — after the close
        assertThat(providerAt(Instant.parse("2026-08-18T21:00:00Z"), false).getMarketStatus())
                .isEqualTo(MarketStatus.CLOSED);
        // Sunday — closed
        assertThat(providerAt(Instant.parse("2026-08-16T15:00:00Z"), false).getMarketStatus())
                .isEqualTo(MarketStatus.CLOSED);
        // Christmas Day 2026 — holiday
        assertThat(providerAt(Instant.parse("2026-12-25T15:00:00Z"), false).getMarketStatus())
                .isEqualTo(MarketStatus.HOLIDAY);
    }
}
