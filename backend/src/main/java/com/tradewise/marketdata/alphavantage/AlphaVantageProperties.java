package com.tradewise.marketdata.alphavantage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Upstream market data settings.
 *
 * @param apiKey        from the environment; blank disables live data entirely
 *                      (the app then runs on the simulator and still starts)
 * @param baseUrl       provider endpoint
 * @param dailyBudget   maximum upstream requests per calendar day. The free tier
 *                      allows 25; the default leaves deliberate headroom for
 *                      restarts and manual refreshes.
 * @param staleAfterHours a snapshot older than this is eligible for refresh
 * @param timeoutSeconds per-request timeout; on expiry we fall back, never hang a user request
 */
@ConfigurationProperties(prefix = "tradewise.marketdata.alphavantage")
public record AlphaVantageProperties(
        String apiKey,
        String baseUrl,
        int dailyBudget,
        int staleAfterHours,
        int timeoutSeconds
) {

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
