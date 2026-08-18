package com.tradewise.marketdata;

import com.tradewise.marketdata.model.HistoricalData;
import com.tradewise.marketdata.model.MarketStatus;
import com.tradewise.marketdata.model.Quote;
import com.tradewise.marketdata.model.StockSearchResult;
import com.tradewise.marketdata.model.TimeRange;
import java.util.List;
import java.util.Optional;

/**
 * Abstraction over the market data source. Everything downstream depends on this
 * interface only, so the simulator can be swapped for a live feed without touching
 * orders, portfolio or dashboard.
 */
public interface MarketDataProvider {

    Optional<Quote> getQuote(String symbol);

    List<StockSearchResult> search(String keyword);

    Optional<HistoricalData> getHistory(String symbol, TimeRange range);

    MarketStatus getMarketStatus();
}
