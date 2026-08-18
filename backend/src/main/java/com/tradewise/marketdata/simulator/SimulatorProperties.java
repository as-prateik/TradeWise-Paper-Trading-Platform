package com.tradewise.marketdata.simulator;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param alwaysOpen Phase 1 decision: with the simulator there is no reason to block
 *                   trading by wall-clock, so the market reports OPEN around the clock.
 *                   Set false to derive status from real NSE hours (IST) instead —
 *                   which is also what a live provider would do.
 */
@ConfigurationProperties(prefix = "tradewise.marketdata.simulator")
public record SimulatorProperties(boolean alwaysOpen) {
}
