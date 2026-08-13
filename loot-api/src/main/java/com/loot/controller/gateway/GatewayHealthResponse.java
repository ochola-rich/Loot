package com.loot.controller.gateway;

import java.time.Instant;

public record GatewayHealthResponse(
        String name,
        String status,
        double successRate,
        double avgLatencyMs,
        Instant lastChecked) {
}
