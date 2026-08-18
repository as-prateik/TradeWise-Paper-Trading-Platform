package com.tradewise.dashboard;

import com.tradewise.dashboard.dto.DashboardResponse;
import java.util.UUID;

/**
 * Read-only cross-module aggregation. This module never mutates anything.
 */
public interface DashboardService {

    DashboardResponse getDashboard(UUID userId);
}
