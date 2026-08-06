package com.loot.gateway.orchestration;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks per-gateway success rate over a rolling window of the last 100
 * outcomes, plus lifetime totals. Feeds CountryBasedRoutingStrategy's
 * "avoid gateways under 80% success" check.
 */
@Component
public class GatewayHealthRegistry {

    private static final int WINDOW_SIZE = 100;
    private static final double HEALTHY_THRESHOLD = 0.8;

    private final Map<String, GatewayMetrics> metricsByGateway = new ConcurrentHashMap<>();

    public void record(String gatewayName, boolean success) {
        record(gatewayName, success, -1);
    }

    public void record(String gatewayName, boolean success, long responseTimeMillis) {
        metricsByGateway.computeIfAbsent(gatewayName, k -> new GatewayMetrics())
                .record(success, responseTimeMillis);
    }

    public double successRate(String gatewayName) {
        GatewayMetrics metrics = metricsByGateway.get(gatewayName);
        return metrics == null ? 1.0 : metrics.successRate();
    }

    public boolean isHealthy(String gatewayName) {
        return successRate(gatewayName) >= HEALTHY_THRESHOLD;
    }

    public long totalRequests(String gatewayName) {
        GatewayMetrics metrics = metricsByGateway.get(gatewayName);
        return metrics == null ? 0 : metrics.totalRequests.get();
    }

    public long lastResponseTimeMillis(String gatewayName) {
        GatewayMetrics metrics = metricsByGateway.get(gatewayName);
        return metrics == null ? -1 : metrics.lastResponseTimeMillis.get();
    }

    private static final class GatewayMetrics {
        private final AtomicLong totalRequests = new AtomicLong();
        private final AtomicLong totalSuccesses = new AtomicLong();
        private final AtomicLong totalFailures = new AtomicLong();
        private final AtomicLong lastResponseTimeMillis = new AtomicLong(-1);
        private final Deque<Boolean> recentOutcomes = new ArrayDeque<>();

        synchronized void record(boolean success, long responseTimeMillis) {
            totalRequests.incrementAndGet();
            (success ? totalSuccesses : totalFailures).incrementAndGet();
            if (responseTimeMillis >= 0) {
                lastResponseTimeMillis.set(responseTimeMillis);
            }
            recentOutcomes.addLast(success);
            if (recentOutcomes.size() > WINDOW_SIZE) {
                recentOutcomes.removeFirst();
            }
        }

        synchronized double successRate() {
            if (recentOutcomes.isEmpty()) {
                return 1.0; // no data yet - don't let routing avoid an untested gateway
            }
            long successes = recentOutcomes.stream().filter(Boolean::booleanValue).count();
            return (double) successes / recentOutcomes.size();
        }
    }
}
