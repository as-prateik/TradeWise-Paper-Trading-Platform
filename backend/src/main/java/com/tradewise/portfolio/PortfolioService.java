package com.tradewise.portfolio;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The portfolio module's contract: position mutation for the order engine, position
 * reads for portfolio views. Weighted-average cost accounting lives in {@link Holding}.
 */
public interface PortfolioService {

    void applyBuy(UUID userId, String symbol, long quantity, BigDecimal price);

    /**
     * Applies a sell if the position covers it.
     *
     * @return the realized P&L delta of the sell, or empty when shares are insufficient
     *         (recorded by the order engine as a REJECTED order, not thrown)
     */
    Optional<BigDecimal> attemptSell(UUID userId, String symbol, long quantity, BigDecimal price);

    List<Holding> getHoldings(UUID userId);

    /** Valued portfolio: every open position priced via market data, with allocation. */
    com.tradewise.portfolio.dto.PortfolioResponse getPortfolio(UUID userId);
}
