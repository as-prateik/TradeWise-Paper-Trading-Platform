package com.tradewise.marketdata;

import com.tradewise.marketdata.model.HistoricalData;
import com.tradewise.marketdata.model.MarketStatus;
import com.tradewise.marketdata.model.Quote;
import com.tradewise.marketdata.model.StockSearchResult;
import com.tradewise.marketdata.model.TimeRange;
import java.util.List;

/**
 * The market data module's contract for other modules (order, portfolio, dashboard).
 * Unknown symbols surface as ApiException(SYMBOL_NOT_FOUND) here, so callers never
 * deal with empty optionals.
 */
public interface MarketDataService {

    Quote getQuote(String symbol);

    List<StockSearchResult> search(String keyword);

    HistoricalData getHistory(String symbol, TimeRange range);

    MarketStatus getMarketStatus();
}
