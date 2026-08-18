package com.tradewise.marketdata;

import com.tradewise.marketdata.model.HistoricalData;
import com.tradewise.marketdata.model.Quote;
import com.tradewise.marketdata.model.StockSearchResult;
import com.tradewise.marketdata.model.TimeRange;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/market")
@RequiredArgsConstructor
@Validated
public class MarketDataController {

    private final MarketDataService marketDataService;

    public record MarketStatusResponse(String status, Instant asOf) {
    }

    @GetMapping("/search")
    public List<StockSearchResult> search(
            @RequestParam @NotBlank @Size(max = 60) String query) {
        return marketDataService.search(query);
    }

    @GetMapping("/quotes/{symbol}")
    public Quote quote(@PathVariable String symbol) {
        return marketDataService.getQuote(symbol);
    }

    @GetMapping("/history/{symbol}")
    public HistoricalData history(@PathVariable String symbol,
                                  @RequestParam(defaultValue = "ONE_MONTH") TimeRange range) {
        return marketDataService.getHistory(symbol, range);
    }

    @GetMapping("/status")
    public MarketStatusResponse status() {
        return new MarketStatusResponse(marketDataService.getMarketStatus().name(), Instant.now());
    }
}
