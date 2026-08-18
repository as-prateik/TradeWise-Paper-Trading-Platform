package com.tradewise.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.tradewise.marketdata.MarketDataService;
import com.tradewise.marketdata.model.Quote;
import com.tradewise.portfolio.dto.PortfolioResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private HoldingRepository holdingRepository;
    @Mock
    private MarketDataService marketDataService;

    @InjectMocks
    private PortfolioServiceImpl portfolioService;

    private Holding holdingOf(String symbol, long quantity, String averagePrice, String realizedPnl) {
        Holding holding = Holding.empty(USER_ID, symbol);
        if (quantity > 0) {
            holding.applyBuy(quantity, new BigDecimal(averagePrice));
        }
        if (new BigDecimal(realizedPnl).signum() != 0) {
            // realize some P&L by buying and selling one extra share at the target delta
            holding.applyBuy(1, new BigDecimal(averagePrice));
            holding.applySell(1, new BigDecimal(averagePrice).add(new BigDecimal(realizedPnl)));
        }
        return holding;
    }

    private Quote quoteOf(String symbol, String price) {
        return new Quote(symbol, symbol + " Ltd", new BigDecimal(price),
                null, null, null, null, null, null, 0, Instant.now());
    }

    @Test
    @DisplayName("valuation: invested, market value, unrealized P&L and allocation per holding")
    void portfolioValuation() {
        Holding infy = holdingOf("INFY", 10, "1500.00", "0");
        Holding tcs = holdingOf("TCS", 5, "4000.00", "0");
        when(holdingRepository.findByUserIdOrderBySymbolAsc(USER_ID)).thenReturn(List.of(infy, tcs));
        when(marketDataService.getQuote("INFY")).thenReturn(quoteOf("INFY", "1800.00"));
        when(marketDataService.getQuote("TCS")).thenReturn(quoteOf("TCS", "3800.00"));

        PortfolioResponse portfolio = portfolioService.getPortfolio(USER_ID);

        assertThat(portfolio.holdings()).hasSize(2);
        var infyView = portfolio.holdings().getFirst();
        assertThat(infyView.investedValue()).isEqualByComparingTo("15000.0000");
        assertThat(infyView.marketValue()).isEqualByComparingTo("18000.0000");
        assertThat(infyView.unrealizedPnl()).isEqualByComparingTo("3000.0000");
        assertThat(infyView.unrealizedPnlPercent()).isEqualByComparingTo("20.00");
        // allocation: 18000 of 37000 total
        assertThat(infyView.allocationPercent()).isEqualByComparingTo("48.65");

        var totals = portfolio.totals();
        assertThat(totals.investedValue()).isEqualByComparingTo("35000.0000");
        assertThat(totals.marketValue()).isEqualByComparingTo("37000.0000");
        assertThat(totals.unrealizedPnl()).isEqualByComparingTo("2000.0000");
    }

    @Test
    @DisplayName("zero-quantity positions are hidden from the list but their realized P&L survives in totals")
    void closedPositionsKeepRealizedPnl() {
        Holding closed = Holding.empty(USER_ID, "WIPRO");
        closed.applyBuy(10, new BigDecimal("500.00"));
        closed.applySell(10, new BigDecimal("550.00")); // realized +500

        when(holdingRepository.findByUserIdOrderBySymbolAsc(USER_ID)).thenReturn(List.of(closed));

        PortfolioResponse portfolio = portfolioService.getPortfolio(USER_ID);

        assertThat(portfolio.holdings()).isEmpty();
        assertThat(portfolio.totals().realizedPnl()).isEqualByComparingTo("500.0000");
        assertThat(portfolio.totals().marketValue()).isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("an empty portfolio yields zero totals, not errors")
    void emptyPortfolio() {
        when(holdingRepository.findByUserIdOrderBySymbolAsc(USER_ID)).thenReturn(List.of());

        PortfolioResponse portfolio = portfolioService.getPortfolio(USER_ID);

        assertThat(portfolio.holdings()).isEmpty();
        assertThat(portfolio.totals().investedValue()).isEqualByComparingTo("0");
        assertThat(portfolio.totals().realizedPnl()).isEqualByComparingTo("0");
    }
}
