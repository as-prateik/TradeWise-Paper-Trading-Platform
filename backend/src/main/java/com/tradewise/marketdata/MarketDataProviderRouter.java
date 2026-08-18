package com.tradewise.marketdata;

import com.tradewise.marketdata.model.HistoricalData;
import com.tradewise.marketdata.model.MarketStatus;
import com.tradewise.marketdata.model.Quote;
import com.tradewise.marketdata.model.StockSearchResult;
import com.tradewise.marketdata.model.TimeRange;
import com.tradewise.marketdata.simulator.SimulatedMarketDataProvider;
import com.tradewise.marketdata.simulator.SimulatorProperties;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Chooses the data source per request: real stored data when it exists, the
 * deterministic simulator otherwise.
 *
 * <p>This is the seam the {@link MarketDataProvider} interface was designed for. The
 * order engine, portfolio and dashboard depend on that interface alone, so swapping
 * a simulated price for a real one required no change to any of them.
 *
 * <p>Falling back rather than failing is deliberate: a clone with no API key, or an
 * account whose daily budget is spent, still runs end to end. The cost is that prices
 * may be simulated, which is why {@link Quote#source()} tells the client which it is.
 */
@Primary
@Component
@RequiredArgsConstructor
public class MarketDataProviderRouter implements MarketDataProvider {

    private final PersistedMarketDataProvider persistedProvider;
    private final SimulatedMarketDataProvider simulatedProvider;
    private final SimulatorProperties simulatorProperties;

    @Override
    public Optional<Quote> getQuote(String symbol) {
        Optional<Quote> live = persistedProvider.getQuote(symbol);
        return live.isPresent() ? live : simulatedProvider.getQuote(symbol);
    }

    @Override
    public List<StockSearchResult> search(String keyword) {
        // Both providers search the same local universe; no upstream cost either way.
        return persistedProvider.search(keyword);
    }

    @Override
    public Optional<HistoricalData> getHistory(String symbol, TimeRange range) {
        Optional<HistoricalData> live = persistedProvider.getHistory(symbol, range);
        return live.isPresent() ? live : simulatedProvider.getHistory(symbol, range);
    }

    @Override
    public MarketStatus getMarketStatus() {
        // Trading is gated on this, and the data is end-of-day, so honouring the
        // simulator's always-open setting is what keeps the app demoable outside US
        // market hours. Set always-open=false to enforce real session times.
        return simulatorProperties.alwaysOpen() ? MarketStatus.OPEN : simulatedProvider.getMarketStatus();
    }
}
