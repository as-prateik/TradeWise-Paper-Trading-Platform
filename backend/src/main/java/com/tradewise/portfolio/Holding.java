package com.tradewise.portfolio;

import static com.tradewise.common.MoneyConstants.MONEY_SCALE;
import static com.tradewise.common.MoneyConstants.ROUNDING;

import com.tradewise.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * A position in one symbol, carried at weighted-average cost.
 *
 * <p>Weighted average (not FIFO) is the Phase 1 accounting choice: it matches how Indian
 * brokers display positions, needs no lot bookkeeping, and keeps realized P&L a single
 * accumulator per position. The choice is isolated in {@link #applyBuy} / {@link #applySell},
 * so a FIFO implementation could replace it without touching callers.
 *
 * <p>Zero-quantity rows are kept: they carry the position's realized P&L history.
 */
@Entity
@Table(name = "holdings")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Holding extends AuditableEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "average_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal averagePrice;

    @Column(name = "realized_pnl", nullable = false, precision = 19, scale = 4)
    private BigDecimal realizedPnl;

    /** Optimistic lock guard: two concurrent executions on the same position conflict here. */
    @Version
    @Column(nullable = false)
    private long version;

    public static Holding empty(UUID userId, String symbol) {
        return Holding.builder()
                .userId(userId)
                .symbol(symbol)
                .quantity(0)
                .averagePrice(BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING))
                .realizedPnl(BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING))
                .build();
    }

    /** newAverage = (oldQty * oldAvg + qty * price) / (oldQty + qty), HALF_UP at scale 4. */
    public void applyBuy(long buyQuantity, BigDecimal price) {
        BigDecimal oldCost = averagePrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal addedCost = price.multiply(BigDecimal.valueOf(buyQuantity));
        long newQuantity = quantity + buyQuantity;
        this.averagePrice = oldCost.add(addedCost)
                .divide(BigDecimal.valueOf(newQuantity), MONEY_SCALE, ROUNDING);
        this.quantity = newQuantity;
    }

    /**
     * Realized P&L for the sold shares: (sellPrice - averagePrice) * qty.
     * The average price of the remaining shares does not change on a sell.
     *
     * @return the realized P&L delta of this sell
     */
    public BigDecimal applySell(long sellQuantity, BigDecimal price) {
        if (sellQuantity > quantity) {
            throw new IllegalStateException("Attempted to sell more shares than held");
        }
        BigDecimal realizedDelta = price.subtract(averagePrice)
                .multiply(BigDecimal.valueOf(sellQuantity))
                .setScale(MONEY_SCALE, ROUNDING);
        this.realizedPnl = realizedPnl.add(realizedDelta);
        this.quantity = quantity - sellQuantity;
        return realizedDelta;
    }
}
