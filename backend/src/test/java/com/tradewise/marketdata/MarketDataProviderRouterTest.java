package com.tradewise.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tradewise.marketdata.model.DataSource;
import com.tradewise.marketdata.model.MarketStatus;
import com.tradewise.marketdata.model.Quote;
import com.tradewise.marketdata.model.TimeRange;
import com.tradewise.marketdata.simulator.SimulatedMarketDataProvider;
import com.tradewise.marketdata.simulator.SimulatorProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * The router is what makes the app runnable with or without an API key, so its
 * fallback behaviour is tested directly rather than assumed.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketDataProviderRouterTest {

    @Mock
    private PersistedMarketDataProvider persisted;
    @Mock
    private SimulatedMarketDataProvider simulated;

    private MarketDataProviderRouter router(boolean alwaysOpen) {
        return new MarketDataProviderRouter(persisted, simulated, new SimulatorProperties(alwaysOpen));
    }

    private Quote quote(String symbol, String price, DataSource source) {
        return new Quote(symbol, symbol + " Inc.", new BigDecimal(price),
                null, null, null, null, null, null, 0,
                LocalDate.of(2026, 8, 17), source, Instant.parse("2026-08-18T06:00:00Z"));
    }

    @Test
    @DisplayName("real stored data wins and the simulator is never consulted")
    void prefersRealData() {
        when(persisted.getQuote("AAPL")).thenReturn(Optional.of(quote("AAPL", "305.59", DataSource.LIVE)));

        Optional<Quote> result = router(true).getQuote("AAPL");

        assertThat(result).isPresent();
        assertThat(result.get().price()).isEqualByComparingTo("305.59");
        assertThat(result.get().source()).isEqualTo(DataSource.LIVE);
        verifyNoInteractions(simulated);
    }

    @Test
    @DisplayName("with no stored snapshot it falls back to the simulator instead of failing")
    void fallsBackToSimulator() {
        when(persisted.getQuote("AAPL")).thenReturn(Optional.empty());
        when(simulated.getQuote("AAPL")).thenReturn(Optional.of(quote("AAPL", "305.00", DataSource.SIMULATED)));

        Optional<Quote> result = router(true).getQuote("AAPL");

        assertThat(result).isPresent();
        assertThat(result.get().source()).isEqualTo(DataSource.SIMULATED);
    }

    @Test
    @DisplayName("history falls back the same way, so charts never come back empty")
    void historyFallsBack() {
        when(persisted.getHistory("AAPL", TimeRange.ONE_MONTH)).thenReturn(Optional.empty());
        when(simulated.getHistory("AAPL", TimeRange.ONE_MONTH)).thenReturn(Optional.empty());

        assertThat(router(true).getHistory("AAPL", TimeRange.ONE_MONTH)).isEmpty();
    }

    @Test
    @DisplayName("always-open keeps the app tradable outside US session hours")
    void alwaysOpenOverridesSessionHours() {
        assertThat(router(true).getMarketStatus()).isEqualTo(MarketStatus.OPEN);

        when(simulated.getMarketStatus()).thenReturn(MarketStatus.CLOSED);
        assertThat(router(false).getMarketStatus()).isEqualTo(MarketStatus.CLOSED);
    }
}
