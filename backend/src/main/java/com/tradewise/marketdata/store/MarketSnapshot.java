package com.tradewise.marketdata.store;

import com.tradewise.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The most recent real closing data for one symbol, as reported by the upstream
 * provider. Keyed by symbol: there is exactly one current snapshot per instrument,
 * replaced on each daily refresh.
 */
@Entity
@Table(name = "market_snapshots")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MarketSnapshot extends AuditableEntity {

    @Id
    @Column(length = 20)
    private String symbol;

    @Column(name = "company_name", nullable = false, length = 120)
    private String companyName;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "day_open", nullable = false, precision = 19, scale = 4)
    private BigDecimal dayOpen;

    @Column(name = "day_high", nullable = false, precision = 19, scale = 4)
    private BigDecimal dayHigh;

    @Column(name = "day_low", nullable = false, precision = 19, scale = 4)
    private BigDecimal dayLow;

    @Column(name = "previous_close", nullable = false, precision = 19, scale = 4)
    private BigDecimal previousClose;

    @Column(nullable = false)
    private long volume;

    /** The trading day this close belongs to — not the time we fetched it. */
    @Column(name = "trading_day", nullable = false)
    private LocalDate tradingDay;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;
}
