package com.tradewise.marketdata.simulator;

import com.tradewise.marketdata.MarketDataProvider;
import com.tradewise.marketdata.model.Candle;
import com.tradewise.marketdata.model.DataSource;
import com.tradewise.marketdata.model.HistoricalData;
import com.tradewise.marketdata.model.MarketStatus;
import com.tradewise.marketdata.model.Quote;
import com.tradewise.marketdata.model.StockSearchResult;
import com.tradewise.marketdata.model.TimeRange;
import com.tradewise.marketdata.simulator.StockUniverse.ListedStock;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Deterministic market simulator.
 *
 * <p>The price of a symbol at an instant is a pure function of (symbol, minute): layered
 * sinusoids with symbol-seeded phases plus bounded hash noise around a fixed reference
 * price. Determinism is the point — quotes, charts and order-engine tests all agree on
 * the same price for the same instant, with no state to drift and no feed required.
 */
@Component
public class SimulatedMarketDataProvider implements MarketDataProvider {

    private static final ZoneId MARKET_ZONE = ZoneId.of("America/New_York");
    private static final LocalTime PRE_OPEN_START = LocalTime.of(9, 0);
    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 30);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(16, 0);
    /** A small fixed 2026 US market holiday sample; a live provider would supply the full calendar. */
    private static final Set<LocalDate> HOLIDAYS = Set.of(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 7, 3),
            LocalDate.of(2026, 11, 26),
            LocalDate.of(2026, 12, 25));

    private static final int PRICE_SCALE = 2;

    private final Clock clock;
    private final SimulatorProperties properties;

    public SimulatedMarketDataProvider(Clock clock, SimulatorProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    @Override
    public Optional<Quote> getQuote(String symbol) {
        ListedStock stock = StockUniverse.BY_SYMBOL.get(normalize(symbol));
        if (stock == null) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        ZonedDateTime marketNow = now.atZone(MARKET_ZONE);

        BigDecimal price = priceAt(stock, now);
        BigDecimal dayOpen = priceAt(stock, marketNow.toLocalDate().atTime(MARKET_OPEN).atZone(MARKET_ZONE).toInstant());
        BigDecimal previousClose = priceAt(stock,
                marketNow.toLocalDate().minusDays(1).atTime(MARKET_CLOSE).atZone(MARKET_ZONE).toInstant());

        BigDecimal dayHigh = price.max(dayOpen);
        BigDecimal dayLow = price.min(dayOpen);
        Instant dayStart = marketNow.toLocalDate().atStartOfDay(MARKET_ZONE).toInstant();
        for (Instant sample = dayStart; sample.isBefore(now); sample = sample.plus(Duration.ofMinutes(15))) {
            BigDecimal sampled = priceAt(stock, sample);
            dayHigh = dayHigh.max(sampled);
            dayLow = dayLow.min(sampled);
        }

        BigDecimal changeAbsolute = price.subtract(previousClose);
        BigDecimal changePercent = previousClose.signum() == 0 ? BigDecimal.ZERO
                : changeAbsolute.multiply(BigDecimal.valueOf(100))
                        .divide(previousClose, 2, RoundingMode.HALF_UP);

        return Optional.of(new Quote(stock.symbol(), stock.companyName(), price, dayOpen, dayHigh, dayLow,
                previousClose, changeAbsolute, changePercent, volumeAt(stock, now),
                marketNow.toLocalDate(), DataSource.SIMULATED, now));
    }

    @Override
    public List<StockSearchResult> search(String keyword) {
        String needle = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return List.of();
        }
        return StockUniverse.STOCKS.stream()
                .filter(stock -> stock.symbol().toLowerCase(Locale.ROOT).contains(needle)
                        || stock.companyName().toLowerCase(Locale.ROOT).contains(needle))
                .map(stock -> new StockSearchResult(stock.symbol(), stock.companyName(), stock.exchange()))
                .toList();
    }

    @Override
    public Optional<HistoricalData> getHistory(String symbol, TimeRange range) {
        ListedStock stock = StockUniverse.BY_SYMBOL.get(normalize(symbol));
        if (stock == null) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        Duration bucket = bucketFor(range);
        int bucketCount = bucketCountFor(range);

        List<Candle> candles = new ArrayList<>(bucketCount);
        Instant cursor = now.minus(bucket.multipliedBy(bucketCount));
        for (int i = 0; i < bucketCount; i++) {
            Instant bucketStart = cursor;
            Instant bucketEnd = cursor.plus(bucket);
            BigDecimal open = priceAt(stock, bucketStart);
            BigDecimal close = priceAt(stock, bucketEnd);
            BigDecimal midOne = priceAt(stock, bucketStart.plus(bucket.dividedBy(3)));
            BigDecimal midTwo = priceAt(stock, bucketStart.plus(bucket.dividedBy(3).multipliedBy(2)));
            BigDecimal high = open.max(close).max(midOne).max(midTwo);
            BigDecimal low = open.min(close).min(midOne).min(midTwo);
            candles.add(new Candle(bucketEnd, open, high, low, close, volumeAt(stock, bucketEnd)));
            cursor = bucketEnd;
        }
        return Optional.of(new HistoricalData(stock.symbol(), range, candles));
    }

    @Override
    public MarketStatus getMarketStatus() {
        if (properties.alwaysOpen()) {
            return MarketStatus.OPEN;
        }
        ZonedDateTime marketNow = clock.instant().atZone(MARKET_ZONE);
        LocalDate date = marketNow.toLocalDate();
        if (HOLIDAYS.contains(date)) {
            return MarketStatus.HOLIDAY;
        }
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return MarketStatus.CLOSED;
        }
        LocalTime time = marketNow.toLocalTime();
        if (!time.isBefore(PRE_OPEN_START) && time.isBefore(MARKET_OPEN)) {
            return MarketStatus.PRE_OPEN;
        }
        if (!time.isBefore(MARKET_OPEN) && time.isBefore(MARKET_CLOSE)) {
            return MarketStatus.OPEN;
        }
        return MarketStatus.CLOSED;
    }

    /**
     * The deterministic price function: reference price modulated by layered cycles
     * (month, day, hour-ish, quarter-hour) plus bounded per-minute hash noise.
     */
    BigDecimal priceAt(ListedStock stock, Instant instant) {
        long minute = instant.getEpochSecond() / 60;
        long seed = fnvHash(stock.symbol());
        double phaseOne = (seed % 628) / 100.0;
        double phaseTwo = ((seed / 628) % 628) / 100.0;
        double phaseThree = ((seed / 394_384) % 628) / 100.0;

        double relative =
                0.030 * Math.sin(2 * Math.PI * minute / (390.0 * 22) + phaseOne)
                        + 0.015 * Math.sin(2 * Math.PI * minute / 390.0 + phaseTwo)
                        + 0.008 * Math.sin(2 * Math.PI * minute / 55.0 + phaseThree)
                        + 0.004 * Math.sin(2 * Math.PI * minute / 13.0 + phaseOne)
                        + 0.003 * noise(seed, minute);

        BigDecimal factor = BigDecimal.valueOf(1 + relative);
        return stock.referencePrice().multiply(factor, MathContext.DECIMAL64)
                .setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    long volumeAt(ListedStock stock, Instant instant) {
        long minute = instant.getEpochSecond() / 60;
        long mixed = mix(fnvHash(stock.symbol()) ^ (minute * 0x9E3779B97F4A7C15L));
        return 10_000 + Math.floorMod(mixed, 90_000);
    }

    /** Deterministic pseudo-noise in [-1, 1] from (symbol seed, minute). */
    private double noise(long seed, long minute) {
        long mixed = mix(seed ^ (minute * 0x9E3779B97F4A7C15L));
        return (Math.floorMod(mixed, 2_000_001) - 1_000_000) / 1_000_000.0;
    }

    private static long mix(long value) {
        long mixed = value;
        mixed ^= mixed >>> 33;
        mixed *= 0xFF51AFD7ED558CCDL;
        mixed ^= mixed >>> 33;
        mixed *= 0xC4CEB9FE1A85EC53L;
        mixed ^= mixed >>> 33;
        return mixed;
    }

    private static long fnvHash(String value) {
        long hash = 0xCBF29CE484222325L;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x100000001B3L;
        }
        return hash;
    }

    private Duration bucketFor(TimeRange range) {
        return switch (range) {
            case ONE_DAY -> Duration.ofMinutes(5);
            case ONE_WEEK -> Duration.ofHours(1);
            case ONE_MONTH, THREE_MONTHS -> Duration.ofDays(1);
            case ONE_YEAR -> Duration.ofDays(7);
        };
    }

    private int bucketCountFor(TimeRange range) {
        return switch (range) {
            case ONE_DAY -> 78;
            case ONE_WEEK -> 168;
            case ONE_MONTH -> 30;
            case THREE_MONTHS -> 90;
            case ONE_YEAR -> 52;
        };
    }

    private String normalize(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }
}
