package com.tradewise.order;

import com.tradewise.common.PageResponse;
import com.tradewise.order.dto.OrderResponse;
import com.tradewise.order.dto.PlaceOrderRequest;
import com.tradewise.order.dto.TradeResponse;
import com.tradewise.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class OrderController {

    private static final int MAX_PAGE_SIZE = 100;

    private final OrderService orderService;

    /**
     * Places a market order. Responds 201 for a newly processed order (whatever its
     * outcome — EXECUTED and REJECTED are both first-class results recorded in history)
     * and 200 when the Idempotency-Key identifies an already-processed submission.
     */
    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> placeOrder(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 64) String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest request) {
        OrderService.PlacementResult result = orderService.placeOrder(principal.userId(), request, idempotencyKey);
        HttpStatus status = result.idempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(OrderResponse.from(result.order()));
    }

    @GetMapping("/orders/{orderId}")
    public OrderResponse getOrder(@AuthenticationPrincipal AuthenticatedUser principal,
                                  @PathVariable UUID orderId) {
        return OrderResponse.from(orderService.getOwnOrder(principal.userId(), orderId));
    }

    @GetMapping("/orders")
    public PageResponse<OrderResponse> getOrders(@AuthenticationPrincipal AuthenticatedUser principal,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(orderService.getOrders(principal.userId(),
                PageRequest.of(Math.max(0, page), clampSize(size))), OrderResponse::from);
    }

    @GetMapping("/trades")
    public PageResponse<TradeResponse> getTrades(@AuthenticationPrincipal AuthenticatedUser principal,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(orderService.getTrades(principal.userId(),
                PageRequest.of(Math.max(0, page), clampSize(size))), TradeResponse::from);
    }

    private int clampSize(int size) {
        return Math.min(Math.max(1, size), MAX_PAGE_SIZE);
    }
}
