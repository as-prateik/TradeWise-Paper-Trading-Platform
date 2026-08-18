package com.tradewise.marketdata.simulator;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The fixed set of simulated instruments. Reference prices are realistic anchors,
 * not live values.
 */
public final class StockUniverse {

    public record ListedStock(String symbol, String companyName, String exchange, BigDecimal referencePrice) {
    }

    public static final List<ListedStock> STOCKS = List.of(
            stock("RELIANCE", "Reliance Industries Ltd", "2850.00"),
            stock("TCS", "Tata Consultancy Services Ltd", "4100.00"),
            stock("INFY", "Infosys Ltd", "1850.00"),
            stock("HDFCBANK", "HDFC Bank Ltd", "1650.00"),
            stock("ICICIBANK", "ICICI Bank Ltd", "1150.00"),
            stock("SBIN", "State Bank of India", "820.00"),
            stock("BHARTIARTL", "Bharti Airtel Ltd", "1450.00"),
            stock("ITC", "ITC Ltd", "440.00"),
            stock("LT", "Larsen & Toubro Ltd", "3600.00"),
            stock("HINDUNILVR", "Hindustan Unilever Ltd", "2500.00"),
            stock("BAJFINANCE", "Bajaj Finance Ltd", "7200.00"),
            stock("MARUTI", "Maruti Suzuki India Ltd", "12500.00"),
            stock("TATAMOTORS", "Tata Motors Ltd", "980.00"),
            stock("SUNPHARMA", "Sun Pharmaceutical Industries Ltd", "1700.00"),
            stock("WIPRO", "Wipro Ltd", "520.00"),
            stock("ADANIENT", "Adani Enterprises Ltd", "3100.00"),
            stock("AXISBANK", "Axis Bank Ltd", "1180.00"),
            stock("ASIANPAINT", "Asian Paints Ltd", "2900.00"),
            stock("TITAN", "Titan Company Ltd", "3400.00"),
            stock("NTPC", "NTPC Ltd", "390.00"));

    public static final Map<String, ListedStock> BY_SYMBOL = STOCKS.stream()
            .collect(Collectors.toUnmodifiableMap(ListedStock::symbol, Function.identity()));

    private static ListedStock stock(String symbol, String companyName, String referencePrice) {
        return new ListedStock(symbol, companyName, "NSE", new BigDecimal(referencePrice));
    }

    private StockUniverse() {
    }
}
