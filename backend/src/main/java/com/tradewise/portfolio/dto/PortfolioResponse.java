package com.tradewise.portfolio.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Portfolio view with per-holding valuation and allocation.
 * Zero-quantity positions are excluded from the list; their realized P&L still counts
 * in the totals.
 */
public record PortfolioResponse(List<HoldingView> holdings, PortfolioTotals totals) {

    public record HoldingView(
            String symbol,
            String companyName,
            long quantity,
            BigDecimal averagePrice,
            BigDecimal investedValue,
            BigDecimal currentPrice,
            BigDecimal marketValue,
            BigDecimal unrealizedPnl,
            BigDecimal unrealizedPnlPercent,
            BigDecimal realizedPnl,
            BigDecimal allocationPercent
    ) {
    }

    public record PortfolioTotals(
            BigDecimal investedValue,
            BigDecimal marketValue,
            BigDecimal unrealizedPnl,
            BigDecimal realizedPnl
    ) {
    }
}
