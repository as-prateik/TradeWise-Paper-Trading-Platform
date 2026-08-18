package com.tradewise.dashboard.dto;

import com.tradewise.marketdata.model.MarketStatus;
import com.tradewise.order.dto.OrderResponse;
import com.tradewise.portfolio.dto.PortfolioResponse.PortfolioTotals;
import java.math.BigDecimal;
import java.util.List;

/**
 * The account at a glance: cash + portfolio value + recent activity.
 * totalAccountValue = cash balance + portfolio market value.
 */
public record DashboardResponse(
        BigDecimal cashBalance,
        BigDecimal portfolioMarketValue,
        BigDecimal totalAccountValue,
        BigDecimal investedValue,
        BigDecimal unrealizedPnl,
        BigDecimal realizedPnl,
        int openPositionCount,
        MarketStatus marketStatus,
        List<OrderResponse> recentOrders
) {
}
