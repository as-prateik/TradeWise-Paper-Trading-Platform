package com.tradewise.marketdata;

import static com.tradewise.common.MoneyConstants.ROUNDING;

import com.tradewise.marketdata.model.Candle;
import com.tradewise.marketdata.model.DataSource;
import com.tradewise.marketdata.model.HistoricalData;
import com.tradewise.marketdata.model.MarketStatus;
import com.tradewise.marketdata.model.Quote;
import com.tradewise.marketdata.model.StockSearchResult;
import com.tradewise.marketdata.model.TimeRange;
import com.tradewise.marketdata.simulator.StockUniverse;
import com.tradewise.marketdata.store.MarketCandleRepository;
import com.tradewise.marketdata.store.MarketSnapshot;
import com.tradewise.marketdata.store.MarketSnapshotRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Serves real market data out of the database.
 *
 * <p>It never calls upstream itself — {@link MarketDataRefreshService} owns that, so a
 * user request can never be blocked by, or spend, the daily request budget. If a symbol
 * has no stored snapshot this provider returns empty and the router falls back.
 */
@Component
@RequiredArgsConstructor
public class PersistedMarketDataProvider implements MarketDataProvider {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final MarketSnapshotRepository snapshotRepository;
    private final MarketCandleRepository candleRepository;

    @Override
    public Optional<Quote> getQuote(String symbol) {
        return snapshotRepository.findBySymbol(normalize(symbol)).map(this::toQuote);
    }

    private Quote toQuote(MarketSnapshot snapshot) {
        BigDecimal changeAbsolute = snapshot.getPrice().subtract(snapshot.getPreviousClose());
        BigDecimal changePercent = snapshot.getPreviousClose().signum() == 0
                ? BigDecimal.ZERO
                : changeAbsolute.multiply(HUNDRED).divide(snapshot.getPreviousClose(), 2, ROUNDING);

        return new Quote(
                snapshot.getSymbol(),
                snapshot.getCompanyName(),
                snapshot.getPrice(),
                snapshot.getDayOpen(),
                snapshot.getDayHigh(),
                snapshot.getDayLow(),
                snapshot.getPreviousClose(),
                changeAbsolute,
                changePercent,
                snapshot.getVolume(),
                snapshot.getTradingDay(),
                DataSource.LIVE,
                snapshot.getFetchedAt());
    }

    @Override
    public List<StockSearchResult> search(String keyword) {
        // Search runs against the local universe: it costs no upstream requests.
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
        String normalized = normalize(symbol);
        LocalDate from = LocalDate.now(ZoneOffset.UTC).minusDays(lookbackDays(range));

        List<Candle> candles = candleRepository
                .findBySymbolAndTradingDayGreaterThanEqualOrderByTradingDayAsc(normalized, from)
                .stream()
                .map(stored -> new Candle(
                        stored.getTradingDay().atStartOfDay(ZoneOffset.UTC).toInstant(),
                        stored.getOpen(),
                        stored.getHigh(),
                        stored.getLow(),
                        stored.getClose(),
                        stored.getVolume()))
                .toList();

        return candles.isEmpty() ? Optional.empty() : Optional.of(new HistoricalData(normalized, range, candles));
    }

    @Override
    public MarketStatus getMarketStatus() {
        // With end-of-day data there is no meaningful intraday state to report; the
        // router decides what to expose. Kept simple and honest.
        return MarketStatus.CLOSED;
    }

    private int lookbackDays(TimeRange range) {
        return switch (range) {
            case ONE_DAY, ONE_WEEK, ONE_MONTH -> 31;
            case THREE_MONTHS -> 92;
            case ONE_YEAR -> 365;
        };
    }

    private String normalize(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }
}
