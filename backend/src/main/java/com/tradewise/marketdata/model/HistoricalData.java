package com.tradewise.marketdata.model;

import java.util.List;

public record HistoricalData(String symbol, TimeRange range, List<Candle> candles) {
}
