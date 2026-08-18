package com.tradewise.marketdata.alphavantage;

/**
 * Upstream could not supply data: transport failure, throttling, or an error
 * payload. Always handled by falling back to the simulator — it never reaches a
 * user as a 5xx.
 */
public class MarketDataUnavailableException extends RuntimeException {

    public MarketDataUnavailableException(String message) {
        super(message);
    }

    public MarketDataUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
