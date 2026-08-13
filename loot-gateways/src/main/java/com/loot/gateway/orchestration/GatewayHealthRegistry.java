package com.loot.gateway.orchestration;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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

    /** Average of the response times recorded within the same rolling window
     * successRate() uses, or -1 if no timed call has been recorded yet. */
    public double avgResponseTimeMillis(String gatewayName) {
        GatewayMetrics metrics = metricsByGateway.get(gatewayName);
        return metrics == null ? -1 : metrics.avgResponseTimeMillis();
    }

    /** When record() last ran for this gateway, or null if it never has. */
    public Instant lastCheckedAt(String gatewayName) {
        GatewayMetrics metrics = metricsByGateway.get(gatewayName);
        return metrics == null ? null : metrics.lastCheckedAt.get();
    }

    private static final class GatewayMetrics {
        private final AtomicLong totalRequests = new AtomicLong();
        private final AtomicLong totalSuccesses = new AtomicLong();
        private final AtomicLong totalFailures = new AtomicLong();
        private final AtomicLong lastResponseTimeMillis = new AtomicLong(-1);
        private final AtomicReference<Instant> lastCheckedAt = new AtomicReference<>();
        private final Deque<Boolean> recentOutcomes = new ArrayDeque<>();
        private final Deque<Long> recentResponseTimes = new ArrayDeque<>();

        synchronized void record(boolean success, long responseTimeMillis) {
            totalRequests.incrementAndGet();
            (success ? totalSuccesses : totalFailures).incrementAndGet();
            lastCheckedAt.set(Instant.now());
            if (responseTimeMillis >= 0) {
                lastResponseTimeMillis.set(responseTimeMillis);
                recentResponseTimes.addLast(responseTimeMillis);
                if (recentResponseTimes.size() > WINDOW_SIZE) {
                    recentResponseTimes.removeFirst();
                }
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

        synchronized double avgResponseTimeMillis() {
            if (recentResponseTimes.isEmpty()) {
                return -1;
            }
            return recentResponseTimes.stream().mapToLong(Long::longValue).average().orElse(-1);
        }
    }
}
