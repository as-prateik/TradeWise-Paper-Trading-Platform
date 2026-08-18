package com.tradewise.marketdata;

import com.tradewise.marketdata.alphavantage.AlphaVantageClient;
import com.tradewise.marketdata.alphavantage.AlphaVantageProperties;
import com.tradewise.marketdata.alphavantage.DailySeries;
import com.tradewise.marketdata.alphavantage.DailySeries.DailyBar;
import com.tradewise.marketdata.alphavantage.MarketDataUnavailableException;
import com.tradewise.marketdata.simulator.StockUniverse;
import com.tradewise.marketdata.simulator.StockUniverse.ListedStock;
import com.tradewise.marketdata.store.MarketCandle;
import com.tradewise.marketdata.store.MarketCandleRepository;
import com.tradewise.marketdata.store.MarketSnapshot;
import com.tradewise.marketdata.store.MarketSnapshotRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns every upstream call.
 *
 * <p>The free tier allows 25 requests per day and one request per symbol returns both
 * the latest close and 100 days of history, so a full refresh of the twelve-symbol
 * universe costs twelve requests. Two guards keep us inside that:
 *
 * <ul>
 *   <li><b>Staleness</b> — a symbol is only refreshed if its stored snapshot is older
 *       than the configured window, so restarts are free.</li>
 *   <li><b>Budget</b> — spend is derived from how many snapshots were actually fetched
 *       in the last 24 hours, so the counter survives restarts without extra state.</li>
 * </ul>
 *
 * <p>Any failure leaves the previous snapshot in place; there is no partial write and
 * no exception surfaces to a user request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataRefreshService {

    private final AlphaVantageClient client;
    private final AlphaVantageProperties properties;
    private final MarketSnapshotRepository snapshotRepository;
    private final MarketCandleRepository candleRepository;
    private final Clock clock;

    public record RefreshReport(int refreshed, int skippedFresh, int failed, int budgetRemaining) {
    }

    /** Populate on boot so a fresh clone with a key has real prices immediately. */
    @EventListener(ApplicationReadyEvent.class)
    public void refreshOnStartup() {
        if (!properties.isConfigured()) {
            log.info("No market data API key configured — serving simulated prices.");
            return;
        }
        RefreshReport report = refreshStaleSymbols();
        log.info("Market data refresh on startup: {}", report);
    }

    /** 06:30 UTC daily — after the US close, before a typical working day starts. */
    @Scheduled(cron = "0 30 6 * * *")
    public void refreshDaily() {
        if (!properties.isConfigured()) {
            return;
        }
        log.info("Scheduled market data refresh: {}", refreshStaleSymbols());
    }

    public RefreshReport refreshStaleSymbols() {
        int refreshed = 0;
        int skippedFresh = 0;
        int failed = 0;

        for (ListedStock stock : StockUniverse.STOCKS) {
            if (isFresh(stock.symbol())) {
                skippedFresh++;
                continue;
            }
            if (remainingBudget() <= 0) {
                log.warn("Daily market data budget of {} exhausted; remaining symbols stay on their "
                        + "previous data or the simulator.", properties.dailyBudget());
                break;
            }
            try {
                store(stock, client.fetchDailySeries(stock.symbol()));
                refreshed++;
            } catch (MarketDataUnavailableException unavailable) {
                failed++;
                log.warn("Market data refresh failed for {}: {}", stock.symbol(), unavailable.getMessage());
            }
        }
        return new RefreshReport(refreshed, skippedFresh, failed, remainingBudget());
    }

    private boolean isFresh(String symbol) {
        Optional<MarketSnapshot> existing = snapshotRepository.findBySymbol(symbol);
        if (existing.isEmpty()) {
            return false;
        }
        Instant staleBefore = clock.instant().minus(Duration.ofHours(properties.staleAfterHours()));
        return existing.get().getFetchedAt().isAfter(staleBefore);
    }

    /**
     * Requests still available today. Derived from stored fetch timestamps rather than
     * an in-memory counter, so it is correct across restarts.
     */
    public int remainingBudget() {
        long spent = snapshotRepository.countByFetchedAtAfter(clock.instant().minus(Duration.ofDays(1)));
        return Math.max(0, properties.dailyBudget() - (int) spent);
    }

    @Transactional
    protected void store(ListedStock stock, DailySeries series) {
        DailyBar latest = series.latest();
        DailyBar previous = series.previous();
        Instant now = clock.instant();

        snapshotRepository.save(MarketSnapshot.builder()
                .symbol(stock.symbol())
                .companyName(stock.companyName())
                .price(latest.close())
                .dayOpen(latest.open())
                .dayHigh(latest.high())
                .dayLow(latest.low())
                .previousClose(previous.close())
                .volume(latest.volume())
                .tradingDay(latest.tradingDay())
                .fetchedAt(now)
                .build());

        // Replace rather than merge: the upstream window is authoritative for this symbol.
        candleRepository.deleteBySymbol(stock.symbol());
        List<MarketCandle> candles = series.bars().stream()
                .map(bar -> MarketCandle.builder()
                        .symbol(stock.symbol())
                        .tradingDay(bar.tradingDay())
                        .open(bar.open())
                        .high(bar.high())
                        .low(bar.low())
                        .close(bar.close())
                        .volume(bar.volume())
                        .build())
                .toList();
        candleRepository.saveAll(candles);
    }
}
