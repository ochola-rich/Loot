package com.loot.controller.gateway;

import com.loot.gateway.orchestration.GatewayHealthRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/gateways")
@Tag(name = "Gateway Health")
public class GatewayHealthController {

    private static final List<String> GATEWAY_NAMES = List.of("MPESA", "FLUTTERWAVE");

    private final GatewayHealthRegistry healthRegistry;

    public GatewayHealthController(GatewayHealthRegistry healthRegistry) {
        this.healthRegistry = healthRegistry;
    }

    @Operation(summary = "Get gateway health",
            description = "Real-time success rate, average latency, and status per payment gateway.")
    @GetMapping("/health")
    public List<GatewayHealthResponse> health() {
        return GATEWAY_NAMES.stream().map(this::toResponse).toList();
    }

    private GatewayHealthResponse toResponse(String name) {
        return new GatewayHealthResponse(
                name,
                healthRegistry.isHealthy(name) ? "HEALTHY" : "UNHEALTHY",
                healthRegistry.successRate(name),
                healthRegistry.avgResponseTimeMillis(name),
                healthRegistry.lastCheckedAt(name));
    }
}
