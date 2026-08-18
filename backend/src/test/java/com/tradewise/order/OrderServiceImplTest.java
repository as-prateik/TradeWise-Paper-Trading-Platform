package com.tradewise.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradewise.common.OrderSide;
import com.tradewise.exception.ApiException;
import com.tradewise.exception.ErrorCode;
import com.tradewise.marketdata.MarketDataService;
import com.tradewise.marketdata.model.MarketStatus;
import com.tradewise.marketdata.model.Quote;
import com.tradewise.order.OrderService.PlacementResult;
import com.tradewise.order.dto.PlaceOrderRequest;
import com.tradewise.portfolio.PortfolioService;
import com.tradewise.wallet.WalletService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-08-18T06:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private TradeRepository tradeRepository;
    @Mock
    private MarketDataService marketDataService;
    @Mock
    private WalletService walletService;
    @Mock
    private PortfolioService portfolioService;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(orderRepository, tradeRepository, marketDataService,
                walletService, portfolioService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Quote quoteAt(String price) {
        return new Quote("RELIANCE", "Reliance Industries Ltd", new BigDecimal(price),
                null, null, null, null, null, null, 0, NOW);
    }

    private void stubNewOrderPersistence() {
        when(orderRepository.findByUserIdAndIdempotencyKey(eq(USER_ID), anyString()))
                .thenReturn(Optional.empty());
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("market buy executes at the quote price: debit, position update, trade record")
    void marketBuyExecutes() {
        stubNewOrderPersistence();
        when(marketDataService.getQuote("RELIANCE")).thenReturn(quoteAt("2800.00"));
        when(marketDataService.getMarketStatus()).thenReturn(MarketStatus.OPEN);
        when(walletService.attemptDebitForTrade(eq(USER_ID), any(), any(), anyString()))
                .thenReturn(Optional.of(new BigDecimal("972000.0000")));

        PlacementResult result = orderService.placeOrder(USER_ID,
                new PlaceOrderRequest("reliance", OrderSide.BUY, OrderType.MARKET, 10), "key-1");

        assertThat(result.idempotentReplay()).isFalse();
        assertThat(result.order().getStatus()).isEqualTo(OrderStatus.EXECUTED);
        assertThat(result.order().getExecutedPrice()).isEqualByComparingTo("2800.0000");
        assertThat(result.order().getExecutedAt()).isEqualTo(NOW);

        ArgumentCaptor<BigDecimal> debited = ArgumentCaptor.forClass(BigDecimal.class);
        verify(walletService).attemptDebitForTrade(eq(USER_ID), debited.capture(), any(), anyString());
        assertThat(debited.getValue()).isEqualByComparingTo("28000.0000");

        verify(portfolioService).applyBuy(eq(USER_ID), eq("RELIANCE"), eq(10L), any());
        verify(tradeRepository).save(any(Trade.class));
    }

    @Test
    @DisplayName("insufficient funds records a REJECTED order and touches no position")
    void insufficientFundsRejects() {
        stubNewOrderPersistence();
        when(marketDataService.getQuote("RELIANCE")).thenReturn(quoteAt("2800.00"));
        when(marketDataService.getMarketStatus()).thenReturn(MarketStatus.OPEN);
        when(walletService.attemptDebitForTrade(eq(USER_ID), any(), any(), anyString()))
                .thenReturn(Optional.empty());

        PlacementResult result = orderService.placeOrder(USER_ID,
                new PlaceOrderRequest("RELIANCE", OrderSide.BUY, OrderType.MARKET, 1000), "key-2");

        assertThat(result.order().getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(result.order().getRejectionReason()).isEqualTo(OrderServiceImpl.REASON_INSUFFICIENT_FUNDS);
        verify(portfolioService, never()).applyBuy(any(), anyString(), anyLong(), any());
        verify(tradeRepository, never()).save(any());
    }

    @Test
    @DisplayName("insufficient shares records a REJECTED sell and credits nothing")
    void insufficientSharesRejects() {
        stubNewOrderPersistence();
        when(marketDataService.getQuote("RELIANCE")).thenReturn(quoteAt("2800.00"));
        when(marketDataService.getMarketStatus()).thenReturn(MarketStatus.OPEN);
        when(portfolioService.attemptSell(eq(USER_ID), eq("RELIANCE"), eq(50L), any()))
                .thenReturn(Optional.empty());

        PlacementResult result = orderService.placeOrder(USER_ID,
                new PlaceOrderRequest("RELIANCE", OrderSide.SELL, OrderType.MARKET, 50), "key-3");

        assertThat(result.order().getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(result.order().getRejectionReason()).isEqualTo(OrderServiceImpl.REASON_INSUFFICIENT_SHARES);
        verify(walletService, never()).creditForTrade(any(), any(), any(), anyString());
    }

    @Test
    @DisplayName("a closed market rejects the order instead of queueing it — a Phase 1 decision")
    void closedMarketRejects() {
        stubNewOrderPersistence();
        when(marketDataService.getQuote("RELIANCE")).thenReturn(quoteAt("2800.00"));
        when(marketDataService.getMarketStatus()).thenReturn(MarketStatus.CLOSED);

        PlacementResult result = orderService.placeOrder(USER_ID,
                new PlaceOrderRequest("RELIANCE", OrderSide.BUY, OrderType.MARKET, 1), "key-4");

        assertThat(result.order().getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(result.order().getRejectionReason()).isEqualTo(OrderServiceImpl.REASON_MARKET_CLOSED);
        verify(walletService, never()).attemptDebitForTrade(any(), any(), any(), anyString());
    }

    @Test
    @DisplayName("a replayed Idempotency-Key returns the original order and executes nothing")
    void idempotentReplayReturnsExistingOrder() {
        Order existing = Order.builder()
                .userId(USER_ID).symbol("RELIANCE").side(OrderSide.BUY).orderType(OrderType.MARKET)
                .quantity(10).status(OrderStatus.EXECUTED).idempotencyKey("key-5")
                .build();
        when(orderRepository.findByUserIdAndIdempotencyKey(USER_ID, "key-5"))
                .thenReturn(Optional.of(existing));

        PlacementResult result = orderService.placeOrder(USER_ID,
                new PlaceOrderRequest("RELIANCE", OrderSide.BUY, OrderType.MARKET, 10), "key-5");

        assertThat(result.idempotentReplay()).isTrue();
        assertThat(result.order()).isSameAs(existing);
        verify(walletService, never()).attemptDebitForTrade(any(), any(), any(), anyString());
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("an unknown symbol is a request error: no order row is recorded")
    void unknownSymbolRecordsNothing() {
        when(orderRepository.findByUserIdAndIdempotencyKey(eq(USER_ID), anyString()))
                .thenReturn(Optional.empty());
        when(marketDataService.getQuote("NOPE"))
                .thenThrow(new ApiException(ErrorCode.SYMBOL_NOT_FOUND, "Unknown symbol: NOPE"));

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID,
                new PlaceOrderRequest("NOPE", OrderSide.BUY, OrderType.MARKET, 1), "key-6"))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SYMBOL_NOT_FOUND));

        verify(orderRepository, never()).saveAndFlush(any());
    }
}
