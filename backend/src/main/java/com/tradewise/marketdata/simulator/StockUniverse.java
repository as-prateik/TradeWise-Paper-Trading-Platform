package com.tradewise.marketdata.simulator;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The tradable universe: twelve US large caps.
 *
 * <p>US equities rather than NSE because free market data APIs cover them; Indian
 * exchange data requires either a paid plan or a broker account, which a reviewer
 * cloning this repo would not have.
 *
 * <p>Twelve symbols is a budget decision, not an aesthetic one: a full refresh costs
 * one upstream request per symbol and the free tier allows 25 per day, so twelve
 * leaves real headroom for restarts and manual refreshes.
 *
 * <p>{@code referencePrice} is only a fallback anchor used by the simulator when live
 * data is unavailable. When a real snapshot exists it is used instead — these numbers
 * are never shown as though they were real quotes.
 */
public final class StockUniverse {

    public record ListedStock(String symbol, String companyName, String exchange, BigDecimal referencePrice) {
    }

    public static final List<ListedStock> STOCKS = List.of(
            stock("AAPL", "Apple Inc.", "NASDAQ", "305.00"),
            stock("MSFT", "Microsoft Corporation", "NASDAQ", "480.00"),
            stock("NVDA", "NVIDIA Corporation", "NASDAQ", "190.00"),
            stock("AMZN", "Amazon.com, Inc.", "NASDAQ", "230.00"),
            stock("GOOGL", "Alphabet Inc. Class A", "NASDAQ", "200.00"),
            stock("META", "Meta Platforms, Inc.", "NASDAQ", "620.00"),
            stock("TSLA", "Tesla, Inc.", "NASDAQ", "340.00"),
            stock("JPM", "JPMorgan Chase & Co.", "NYSE", "280.00"),
            stock("V", "Visa Inc.", "NYSE", "330.00"),
            stock("WMT", "Walmart Inc.", "NYSE", "105.00"),
            stock("KO", "The Coca-Cola Company", "NYSE", "72.00"),
            stock("DIS", "The Walt Disney Company", "NYSE", "115.00"));

    public static final Map<String, ListedStock> BY_SYMBOL = STOCKS.stream()
            .collect(Collectors.toUnmodifiableMap(ListedStock::symbol, Function.identity()));

    private static ListedStock stock(String symbol, String companyName, String exchange, String referencePrice) {
        return new ListedStock(symbol, companyName, exchange, new BigDecimal(referencePrice));
    }

    private StockUniverse() {
    }
}
