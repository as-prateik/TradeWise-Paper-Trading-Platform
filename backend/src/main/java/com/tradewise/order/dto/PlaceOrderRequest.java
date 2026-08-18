package com.tradewise.order.dto;

import com.tradewise.common.OrderSide;
import com.tradewise.order.OrderType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PlaceOrderRequest(

        @NotBlank(message = "Symbol is required")
        @Size(max = 20, message = "Symbol must not exceed 20 characters")
        String symbol,

        @NotNull(message = "Side is required (BUY or SELL)")
        OrderSide side,

        @NotNull(message = "Order type is required (MARKET)")
        OrderType type,

        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 1_000_000, message = "Quantity must not exceed 1,000,000")
        long quantity
) {
}
