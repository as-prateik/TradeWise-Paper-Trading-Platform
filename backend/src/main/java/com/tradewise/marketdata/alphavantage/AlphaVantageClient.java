package com.tradewise.marketdata.alphavantage;

import com.tradewise.marketdata.alphavantage.DailySeries.DailyBar;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Thin client over the TIME_SERIES_DAILY endpoint.
 *
 * <p>Response shape is not guessed — it was verified against live responses:
 * top level {@code "Meta Data"} and {@code "Time Series (Daily)"}; each dated entry
 * carries {@code "1. open"}, {@code "2. high"}, {@code "3. low"}, {@code "4. close"},
 * {@code "5. volume"}. Every numeric value arrives as a <em>string</em>, so each is
 * parsed with {@code new BigDecimal(String)} — never through a double.
 *
 * <p>Failure modes are deliberately collapsed into {@link MarketDataUnavailableException}:
 * the provider chain treats "no data" identically whether the cause is a network
 * error, a rate-limit notice, or a malformed body.
 */
@Slf4j
@Component
public class AlphaVantageClient {

    private static final String TIME_SERIES_KEY = "Time Series (Daily)";
    private static final String META_KEY = "Meta Data";
    /** Alpha Vantage reports throttling and bad symbols as 200 OK with these keys. */
    private static final List<String> ERROR_KEYS = List.of("Note", "Information", "Error Message");

    private final RestClient restClient;
    private final AlphaVantageProperties properties;

    // Explicit: a second (test) constructor exists, so the container must be told
    // which one to use for injection.
    @Autowired
    public AlphaVantageClient(AlphaVantageProperties properties) {
        this.properties = properties;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(properties.timeoutSeconds());
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);

        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /** Package-private seam so tests can drive the parser without a network call. */
    AlphaVantageClient(AlphaVantageProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    public DailySeries fetchDailySeries(String symbol) {
        Map<String, Object> body;
        try {
            body = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/query")
                            .queryParam("function", "TIME_SERIES_DAILY")
                            .queryParam("symbol", symbol)
                            .queryParam("outputsize", "compact")
                            .queryParam("apikey", properties.apiKey())
                            .build())
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {
                    });
        } catch (RestClientException transportFailure) {
            throw new MarketDataUnavailableException(
                    "Upstream request failed for " + symbol, transportFailure);
        }

        return parse(symbol, body);
    }

    DailySeries parse(String symbol, Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            throw new MarketDataUnavailableException("Empty response for " + symbol);
        }
        // Throttling and unknown symbols arrive as HTTP 200 with an advisory key.
        for (String errorKey : ERROR_KEYS) {
            Object advisory = body.get(errorKey);
            if (advisory != null) {
                throw new MarketDataUnavailableException(
                        "Upstream declined request for %s: %s".formatted(symbol, advisory));
            }
        }

        Object series = body.get(TIME_SERIES_KEY);
        if (!(series instanceof Map<?, ?> seriesMap) || seriesMap.isEmpty()) {
            throw new MarketDataUnavailableException("No '" + TIME_SERIES_KEY + "' for " + symbol);
        }

        String resolvedSymbol = resolveSymbol(body, symbol);
        List<DailyBar> bars = new ArrayList<>(seriesMap.size());

        for (Map.Entry<?, ?> entry : seriesMap.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> fields)) {
                continue;
            }
            bars.add(new DailyBar(
                    LocalDate.parse(String.valueOf(entry.getKey())),
                    decimal(fields, "1. open", symbol),
                    decimal(fields, "2. high", symbol),
                    decimal(fields, "3. low", symbol),
                    decimal(fields, "4. close", symbol),
                    Long.parseLong(text(fields, "5. volume", symbol))));
        }

        if (bars.isEmpty()) {
            throw new MarketDataUnavailableException("No usable bars for " + symbol);
        }
        bars.sort(Comparator.comparing(DailyBar::tradingDay));
        return new DailySeries(resolvedSymbol, List.copyOf(bars));
    }

    private String resolveSymbol(Map<String, Object> body, String fallback) {
        if (body.get(META_KEY) instanceof Map<?, ?> meta) {
            Object reported = meta.get("2. Symbol");
            if (reported != null) {
                return String.valueOf(reported);
            }
        }
        return fallback;
    }

    private static BigDecimal decimal(Map<?, ?> fields, String key, String symbol) {
        // String -> BigDecimal directly: constructing from a double would introduce
        // binary rounding error into money before it ever reaches the database.
        return new BigDecimal(text(fields, key, symbol));
    }

    private static String text(Map<?, ?> fields, String key, String symbol) {
        Object value = fields.get(key);
        if (value == null) {
            throw new MarketDataUnavailableException(
                    "Missing field '%s' for %s".formatted(key, symbol));
        }
        return String.valueOf(value).trim();
    }
}
