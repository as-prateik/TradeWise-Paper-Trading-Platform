package com.tradewise.order;

import com.tradewise.common.AuditableEntity;
import com.tradewise.common.OrderSide;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "orders")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Order extends AuditableEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 10)
    private OrderType orderType;

    @Column(nullable = false)
    private long quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrderStatus status;

    @Column(name = "executed_price", precision = 19, scale = 4)
    private BigDecimal executedPrice;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "rejection_reason", length = 100)
    private String rejectionReason;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    public void markExecuted(BigDecimal price, Instant executedAt) {
        this.status = OrderStatus.EXECUTED;
        this.executedPrice = price;
        this.executedAt = executedAt;
    }

    public void markRejected(String reason) {
        this.status = OrderStatus.REJECTED;
        this.rejectionReason = reason;
    }
}
