package com.tradewise.order;

/**
 * Full lifecycle: PENDING -> EXECUTED | CANCELLED | REJECTED | EXPIRED.
 * Phase 1 market orders resolve synchronously to EXECUTED or REJECTED; PENDING,
 * CANCELLED and EXPIRED become reachable with limit/stop orders in Phase 2.
 */
public enum OrderStatus {
    PENDING,
    EXECUTED,
    CANCELLED,
    REJECTED,
    EXPIRED
}
