package com.tradewise.marketdata.alphavantage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tradewise.marketdata.alphavantage.DailySeries.DailyBar;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Parser tests built from an actual upstream response captured during development
 * (AAPL, 17 Aug 2026) — the field names and the fact that every numeric value arrives
 * as a string are taken from real data, not from documentation or memory.
 */
class AlphaVantageClientTest {

    private final AlphaVantageClient client = new AlphaVantageClient(
            new AlphaVantageProperties("test-key", "https://example.invalid", 20, 20, 5), null);

    private Map<String, Object> bar(String open, String high, String low, String close, String volume) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("1. open", open);
        fields.put("2. high", high);
        fields.put("3. low", low);
        fields.put("4. close", close);
        fields.put("5. volume", volume);
        return fields;
    }

    private Map<String, Object> realisticBody() {
        Map<String, Object> series = new LinkedHashMap<>();
        // Upstream returns newest-first; the parser must not rely on that ordering.
        series.put("2026-08-17", bar("306.2100", "307.6600", "302.9390", "305.5900", "38169263"));
        series.put("2026-08-14", bar("306.0000", "307.4900", "304.3000", "305.9300", "28229375"));

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("1. Information", "Daily Prices (open, high, low, close) and Volumes");
        meta.put("2. Symbol", "AAPL");
        meta.put("3. Last Refreshed", "2026-08-17");
        meta.put("4. Output Size", "Compact");
        meta.put("5. Time Zone", "US/Eastern");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("Meta Data", meta);
        body.put("Time Series (Daily)", series);
        return body;
    }

    @Test
    @DisplayName("parses a real response into ordered bars with exact decimal values")
    void parsesRealResponse() {
        DailySeries series = client.parse("AAPL", realisticBody());

        assertThat(series.symbol()).isEqualTo("AAPL");
        assertThat(series.bars()).hasSize(2);

        // oldest-first ordering, regardless of upstream order
        assertThat(series.bars().get(0).tradingDay()).isEqualTo(LocalDate.of(2026, 8, 14));
        assertThat(series.latest().tradingDay()).isEqualTo(LocalDate.of(2026, 8, 17));

        DailyBar latest = series.latest();
        assertThat(latest.close()).isEqualByComparingTo("305.5900");
        assertThat(latest.open()).isEqualByComparingTo("306.2100");
        assertThat(latest.high()).isEqualByComparingTo("307.6600");
        assertThat(latest.low()).isEqualByComparingTo("302.9390");
        assertThat(latest.volume()).isEqualTo(38_169_263L);

        assertThat(series.previous().close()).isEqualByComparingTo("305.9300");
    }

    @Test
    @DisplayName("decimals keep full upstream precision — no double conversion anywhere")
    void keepsExactPrecision() {
        DailySeries series = client.parse("AAPL", realisticBody());

        // 302.9390 survives exactly; a double round-trip would not guarantee this.
        assertThat(series.latest().low()).isEqualTo(new BigDecimal("302.9390"));
    }

    @Test
    @DisplayName("a rate-limit notice returned as HTTP 200 is treated as unavailable, not as data")
    void throttlingIsTreatedAsUnavailable() {
        Map<String, Object> throttled = Map.of(
                "Information", "We have detected your API key ... 25 requests per day");

        assertThatThrownBy(() -> client.parse("AAPL", throttled))
                .isInstanceOf(MarketDataUnavailableException.class)
                .hasMessageContaining("declined");
    }

    @Test
    @DisplayName("an unknown symbol error payload is unavailable, not a crash")
    void errorMessageIsTreatedAsUnavailable() {
        Map<String, Object> error = Map.of("Error Message", "Invalid API call");

        assertThatThrownBy(() -> client.parse("NOPE", error))
                .isInstanceOf(MarketDataUnavailableException.class);
    }

    @Test
    @DisplayName("empty or malformed bodies are rejected rather than silently producing zero prices")
    void malformedBodiesRejected() {
        assertThatThrownBy(() -> client.parse("AAPL", Map.of()))
                .isInstanceOf(MarketDataUnavailableException.class);

        assertThatThrownBy(() -> client.parse("AAPL", Map.of("Time Series (Daily)", Map.of())))
                .isInstanceOf(MarketDataUnavailableException.class);

        Map<String, Object> missingField = Map.of("Time Series (Daily)",
                Map.of("2026-08-17", Map.of("1. open", "306.21")));
        assertThatThrownBy(() -> client.parse("AAPL", missingField))
                .isInstanceOf(MarketDataUnavailableException.class)
                .hasMessageContaining("Missing field");
    }

    @Test
    @DisplayName("a single-bar response still yields a usable previous close")
    void singleBarFallsBackForPreviousClose() {
        Map<String, Object> body = Map.of("Time Series (Daily)",
                Map.of("2026-08-17", bar("306.2100", "307.6600", "302.9390", "305.5900", "38169263")));

        DailySeries series = client.parse("AAPL", body);

        assertThat(series.previous()).isEqualTo(series.latest());
    }
}
