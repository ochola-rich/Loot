package com.loot.controller.gateway;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record GatewayHealthResponse(
        @Schema(description = "Gateway name", example = "MPESA") String name,
        @Schema(description = "HEALTHY if the rolling success rate is at least 80%, else UNHEALTHY", example = "HEALTHY")
        String status,
        @Schema(description = "Success rate over the last 100 outcomes (1.0 if none recorded yet)", example = "0.97")
        double successRate,
        @Schema(description = "Average response time in ms over the last 100 timed calls (-1 if none)", example = "420.5")
        double avgLatencyMs,
        @Schema(description = "When this gateway was last called, or null if never") Instant lastChecked) {
}
