package com.tradewise.order.dto;

import com.tradewise.order.Order;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String symbol,
        String side,
        String type,
        long quantity,
        String status,
        BigDecimal executedPrice,
        Instant executedAt,
        String rejectionReason,
        Instant createdAt
) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(order.getId(), order.getSymbol(), order.getSide().name(),
                order.getOrderType().name(), order.getQuantity(), order.getStatus().name(),
                order.getExecutedPrice(), order.getExecutedAt(), order.getRejectionReason(),
                order.getCreatedAt());
    }
}
