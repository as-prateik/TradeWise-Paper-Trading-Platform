package com.tradewise.dashboard;

import com.tradewise.dashboard.dto.DashboardResponse;
import com.tradewise.exception.ApiException;
import com.tradewise.exception.ErrorCode;
import com.tradewise.marketdata.MarketDataService;
import com.tradewise.order.OrderService;
import com.tradewise.order.dto.OrderResponse;
import com.tradewise.portfolio.PortfolioService;
import com.tradewise.portfolio.dto.PortfolioResponse;
import com.tradewise.wallet.Wallet;
import com.tradewise.wallet.WalletService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final WalletService walletService;
    private final PortfolioService portfolioService;
    private final OrderService orderService;
    private final MarketDataService marketDataService;

    @Override
    public DashboardResponse getDashboard(UUID userId) {
        Wallet wallet = walletService.findByUserId(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Wallet not found"));
        PortfolioResponse portfolio = portfolioService.getPortfolio(userId);
        List<OrderResponse> recentOrders = orderService.getOrders(userId, PageRequest.of(0, 5))
                .getContent().stream().map(OrderResponse::from).toList();

        BigDecimal cash = wallet.getBalance();
        BigDecimal marketValue = portfolio.totals().marketValue();

        return new DashboardResponse(
                cash,
                marketValue,
                cash.add(marketValue),
                portfolio.totals().investedValue(),
                portfolio.totals().unrealizedPnl(),
                portfolio.totals().realizedPnl(),
                portfolio.holdings().size(),
                marketDataService.getMarketStatus(),
                recentOrders);
    }
}
