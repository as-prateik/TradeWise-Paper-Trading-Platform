package com.tradewise.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Weighted-average cost accounting — the number the user judges themselves by.
 */
class HoldingTest {

    private Holding newHolding() {
        return Holding.empty(UUID.randomUUID(), "RELIANCE");
    }

    @Test
    @DisplayName("first buy sets the average to the buy price")
    void firstBuySetsAverage() {
        Holding holding = newHolding();
        holding.applyBuy(10, new BigDecimal("2800.00"));

        assertThat(holding.getQuantity()).isEqualTo(10);
        assertThat(holding.getAveragePrice()).isEqualByComparingTo("2800.00");
    }

    @Test
    @DisplayName("subsequent buys move the average by quantity weight")
    void weightedAverageAcrossBuys() {
        Holding holding = newHolding();
        holding.applyBuy(10, new BigDecimal("100.00"));
        holding.applyBuy(30, new BigDecimal("200.00"));

        // (10*100 + 30*200) / 40 = 175
        assertThat(holding.getQuantity()).isEqualTo(40);
        assertThat(holding.getAveragePrice()).isEqualByComparingTo("175.0000");
    }

    @Test
    @DisplayName("average is rounded HALF_UP at scale 4")
    void averageRoundsHalfUpAtScaleFour() {
        Holding holding = newHolding();
        holding.applyBuy(3, new BigDecimal("100.00"));
        holding.applyBuy(3, new BigDecimal("100.01"));

        // (300 + 300.03) / 6 = 100.005 -> 100.0050
        assertThat(holding.getAveragePrice()).isEqualByComparingTo("100.0050");

        holding.applyBuy(1, new BigDecimal("100.00"));
        // 700.03 / 7 = 100.00428571... -> 100.0043
        assertThat(holding.getAveragePrice()).isEqualByComparingTo("100.0043");
    }

    @Test
    @DisplayName("selling realizes (price - average) * quantity and leaves the average unchanged")
    void sellRealizesPnlAndKeepsAverage() {
        Holding holding = newHolding();
        holding.applyBuy(10, new BigDecimal("100.00"));
        holding.applyBuy(10, new BigDecimal("200.00")); // avg 150

        BigDecimal realized = holding.applySell(5, new BigDecimal("180.00"));

        assertThat(realized).isEqualByComparingTo("150.0000"); // (180-150)*5
        assertThat(holding.getQuantity()).isEqualTo(15);
        assertThat(holding.getAveragePrice()).isEqualByComparingTo("150.0000");
        assertThat(holding.getRealizedPnl()).isEqualByComparingTo("150.0000");
    }

    @Test
    @DisplayName("selling at a loss realizes negative P&L")
    void sellAtLossRealizesNegative() {
        Holding holding = newHolding();
        holding.applyBuy(10, new BigDecimal("500.00"));

        BigDecimal realized = holding.applySell(4, new BigDecimal("450.00"));

        assertThat(realized).isEqualByComparingTo("-200.0000");
        assertThat(holding.getRealizedPnl()).isEqualByComparingTo("-200.0000");
    }

    @Test
    @DisplayName("selling the whole position keeps the row and its realized P&L")
    void sellAllKeepsRealizedPnl() {
        Holding holding = newHolding();
        holding.applyBuy(10, new BigDecimal("100.00"));
        holding.applySell(10, new BigDecimal("120.00"));

        assertThat(holding.getQuantity()).isZero();
        assertThat(holding.getRealizedPnl()).isEqualByComparingTo("200.0000");
    }

    @Test
    @DisplayName("overselling is a programming error, guarded in the entity itself")
    void oversellThrows() {
        Holding holding = newHolding();
        holding.applyBuy(5, new BigDecimal("100.00"));

        assertThatThrownBy(() -> holding.applySell(6, new BigDecimal("100.00")))
                .isInstanceOf(IllegalStateException.class);
    }
}
