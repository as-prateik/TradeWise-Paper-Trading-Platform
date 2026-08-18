package com.tradewise.order;

/**
 * Phase 1 supports market orders only. LIMIT and STOP_LOSS join in Phase 2, together
 * with the matching engine that evaluates pending orders on price movement.
 */
public enum OrderType {
    MARKET
}
