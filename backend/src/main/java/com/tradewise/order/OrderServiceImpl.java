package com.tradewise.order;

import static com.tradewise.common.MoneyConstants.MONEY_SCALE;
import static com.tradewise.common.MoneyConstants.ROUNDING;

import com.tradewise.common.OrderSide;
import com.tradewise.exception.ApiException;
import com.tradewise.exception.ErrorCode;
import com.tradewise.marketdata.MarketDataService;
import com.tradewise.marketdata.model.MarketStatus;
import com.tradewise.marketdata.model.Quote;
import com.tradewise.order.dto.PlaceOrderRequest;
import com.tradewise.portfolio.PortfolioService;
import com.tradewise.wallet.WalletService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The market-order engine.
 *
 * <p>Execution is synchronous and atomic: order row, wallet movement, cash transaction,
 * holding update and trade record commit or roll back together. Business rejections
 * (insufficient funds/shares, market closed) are recorded as REJECTED orders and returned
 * as such — they are outcomes, not errors, and they must appear in order history.
 *
 * <p>Concurrency: wallet and holding carry {@code @Version}. Two overlapping executions
 * for the same user conflict on commit; the loser's transaction rolls back entirely and
 * surfaces as 409 CONCURRENT_MODIFICATION for the client to retry.
 *
 * <p>Idempotency: the client sends an Idempotency-Key header; (user, key) is unique.
 * A replayed submission returns the original order without executing anything twice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    static final String REASON_INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS";
    static final String REASON_INSUFFICIENT_SHARES = "INSUFFICIENT_SHARES";
    static final String REASON_MARKET_CLOSED = "MARKET_CLOSED";

    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;
    private final MarketDataService marketDataService;
    private final WalletService walletService;
    private final PortfolioService portfolioService;
    private final Clock clock;

    @Override
    @Transactional
    public PlacementResult placeOrder(UUID userId, PlaceOrderRequest request, String idempotencyKey) {
        Optional<Order> replay = orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (replay.isPresent()) {
            return new PlacementResult(replay.get(), true);
        }

        String symbol = request.symbol().trim().toUpperCase(Locale.ROOT);
        // Unknown symbol is a request error (no order recorded): SYMBOL_NOT_FOUND propagates.
        Quote quote = marketDataService.getQuote(symbol);

        Order order = Order.builder()
                .userId(userId)
                .symbol(symbol)
                .side(request.side())
                .orderType(request.type())
                .quantity(request.quantity())
                .status(OrderStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .build();
        try {
            order = orderRepository.saveAndFlush(order);
        } catch (DataIntegrityViolationException duplicateSubmission) {
            // Concurrent double-submit: the constraint decides; this transaction is
            // rollback-only, so surface a retryable conflict rather than a broken read.
            throw new ApiException(ErrorCode.CONCURRENT_MODIFICATION,
                    "This order was already submitted; fetch your orders to see its result");
        }

        // Phase 1 decision: an order placed while the market is closed is REJECTED,
        // not queued to open. (The simulator reports OPEN around the clock by default.)
        if (marketDataService.getMarketStatus() != MarketStatus.OPEN) {
            order.markRejected(REASON_MARKET_CLOSED);
            return new PlacementResult(order, false);
        }

        BigDecimal executionPrice = quote.price().setScale(MONEY_SCALE, ROUNDING);
        BigDecimal grossAmount = executionPrice.multiply(BigDecimal.valueOf(request.quantity()))
                .setScale(MONEY_SCALE, ROUNDING);

        if (request.side() == OrderSide.BUY) {
            executeBuy(order, executionPrice, grossAmount);
        } else {
            executeSell(order, executionPrice, grossAmount);
        }
        return new PlacementResult(order, false);
    }

    private void executeBuy(Order order, BigDecimal price, BigDecimal grossAmount) {
        Optional<BigDecimal> balanceAfter = walletService.attemptDebitForTrade(
                order.getUserId(), grossAmount, order.getId(),
                "Buy %d %s @ %s".formatted(order.getQuantity(), order.getSymbol(), price));
        if (balanceAfter.isEmpty()) {
            order.markRejected(REASON_INSUFFICIENT_FUNDS);
            return;
        }
        portfolioService.applyBuy(order.getUserId(), order.getSymbol(), order.getQuantity(), price);
        recordExecution(order, price, grossAmount);
    }

    private void executeSell(Order order, BigDecimal price, BigDecimal grossAmount) {
        Optional<BigDecimal> realizedDelta = portfolioService.attemptSell(
                order.getUserId(), order.getSymbol(), order.getQuantity(), price);
        if (realizedDelta.isEmpty()) {
            order.markRejected(REASON_INSUFFICIENT_SHARES);
            return;
        }
        walletService.creditForTrade(order.getUserId(), grossAmount, order.getId(),
                "Sell %d %s @ %s".formatted(order.getQuantity(), order.getSymbol(), price));
        recordExecution(order, price, grossAmount);
    }

    private void recordExecution(Order order, BigDecimal price, BigDecimal grossAmount) {
        Instant executedAt = clock.instant();
        order.markExecuted(price, executedAt);
        tradeRepository.save(Trade.builder()
                .orderId(order.getId())
                .userId(order.getUserId())
                .symbol(order.getSymbol())
                .side(order.getSide())
                .quantity(order.getQuantity())
                .price(price)
                .grossAmount(grossAmount)
                .executedAt(executedAt)
                .build());
    }

    @Override
    public Order getOwnOrder(UUID userId, UUID orderId) {
        return orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Order not found"));
    }

    @Override
    public Page<Order> getOrders(UUID userId, Pageable pageable) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Override
    public Page<Trade> getTrades(UUID userId, Pageable pageable) {
        return tradeRepository.findByUserIdOrderByExecutedAtDesc(userId, pageable);
    }
}
