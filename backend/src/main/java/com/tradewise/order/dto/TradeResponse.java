package com.tradewise.order.dto;

import com.tradewise.order.Trade;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TradeResponse(
        UUID id,
        UUID orderId,
        String symbol,
        String side,
        long quantity,
        BigDecimal price,
        BigDecimal grossAmount,
        Instant executedAt
) {

    public static TradeResponse from(Trade trade) {
        return new TradeResponse(trade.getId(), trade.getOrderId(), trade.getSymbol(),
                trade.getSide().name(), trade.getQuantity(), trade.getPrice(),
                trade.getGrossAmount(), trade.getExecutedAt());
    }
}
