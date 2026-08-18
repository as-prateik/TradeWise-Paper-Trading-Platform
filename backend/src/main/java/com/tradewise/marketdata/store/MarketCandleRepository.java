package com.tradewise.marketdata.store;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface MarketCandleRepository extends JpaRepository<MarketCandle, UUID> {

    List<MarketCandle> findBySymbolAndTradingDayGreaterThanEqualOrderByTradingDayAsc(
            String symbol, LocalDate from);

    @Transactional
    void deleteBySymbol(String symbol);
}
