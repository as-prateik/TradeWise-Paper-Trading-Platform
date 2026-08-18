package com.tradewise.marketdata.store;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketSnapshotRepository extends JpaRepository<MarketSnapshot, String> {

    Optional<MarketSnapshot> findBySymbol(String symbol);

    /** Drives the daily request budget: how many upstream fetches happened since a point in time. */
    long countByFetchedAtAfter(Instant threshold);

    List<MarketSnapshot> findAllByOrderBySymbolAsc();
}
