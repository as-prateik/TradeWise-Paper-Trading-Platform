package com.tradewise.marketdata;

import com.tradewise.exception.ApiException;
import com.tradewise.exception.ErrorCode;
import com.tradewise.marketdata.model.HistoricalData;
import com.tradewise.marketdata.model.MarketStatus;
import com.tradewise.marketdata.model.Quote;
import com.tradewise.marketdata.model.StockSearchResult;
import com.tradewise.marketdata.model.TimeRange;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MarketDataServiceImpl implements MarketDataService {

    private final MarketDataProvider marketDataProvider;

    @Override
    public Quote getQuote(String symbol) {
        return marketDataProvider.getQuote(symbol)
                .orElseThrow(() -> unknownSymbol(symbol));
    }

    @Override
    public List<StockSearchResult> search(String keyword) {
        return marketDataProvider.search(keyword);
    }

    @Override
    public HistoricalData getHistory(String symbol, TimeRange range) {
        return marketDataProvider.getHistory(symbol, range)
                .orElseThrow(() -> unknownSymbol(symbol));
    }

    @Override
    public MarketStatus getMarketStatus() {
        return marketDataProvider.getMarketStatus();
    }

    private ApiException unknownSymbol(String symbol) {
        return new ApiException(ErrorCode.SYMBOL_NOT_FOUND, "Unknown symbol: " + symbol);
    }
}
