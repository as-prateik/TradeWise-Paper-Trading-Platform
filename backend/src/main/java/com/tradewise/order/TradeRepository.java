package com.tradewise.order;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeRepository extends JpaRepository<Trade, UUID> {

    Page<Trade> findByUserIdOrderByExecutedAtDesc(UUID userId, Pageable pageable);
}
