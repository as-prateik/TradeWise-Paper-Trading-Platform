package com.tradewise.order;

import com.tradewise.order.dto.PlaceOrderRequest;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {

    /**
     * Places and (for market orders) synchronously executes an order.
     *
     * @return the resulting order and whether this call was an idempotent replay
     */
    PlacementResult placeOrder(UUID userId, PlaceOrderRequest request, String idempotencyKey);

    record PlacementResult(Order order, boolean idempotentReplay) {
    }

    Order getOwnOrder(UUID userId, UUID orderId);

    Page<Order> getOrders(UUID userId, Pageable pageable);

    Page<Trade> getTrades(UUID userId, Pageable pageable);
}
