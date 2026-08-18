package com.tradewise.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the daily market data refresh. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
