package com.loot.gateway.orchestration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GatewayHealthRegistryTest {

    @Test
    void noDataYetIsTreatedAsHealthy() {
        GatewayHealthRegistry registry = new GatewayHealthRegistry();

        assertThat(registry.successRate("MPESA")).isEqualTo(1.0);
        assertThat(registry.isHealthy("MPESA")).isTrue();
    }

    @Test
    void calculatesSuccessRateFromKnownOutcomes() {
        GatewayHealthRegistry registry = new GatewayHealthRegistry();

        for (int i = 0; i < 8; i++) {
            registry.record("MPESA", true);
        }
        for (int i = 0; i < 2; i++) {
            registry.record("MPESA", false);
        }

        assertThat(registry.successRate("MPESA")).isCloseTo(0.8, within(0.0001));
        assertThat(registry.isHealthy("MPESA")).isTrue();
        assertThat(registry.totalRequests("MPESA")).isEqualTo(10);
    }

    @Test
    void belowEightyPercentIsUnhealthy() {
        GatewayHealthRegistry registry = new GatewayHealthRegistry();

        for (int i = 0; i < 7; i++) {
            registry.record("FLUTTERWAVE", true);
        }
        for (int i = 0; i < 3; i++) {
            registry.record("FLUTTERWAVE", false);
        }

        assertThat(registry.successRate("FLUTTERWAVE")).isCloseTo(0.7, within(0.0001));
        assertThat(registry.isHealthy("FLUTTERWAVE")).isFalse();
    }

    @Test
    void windowRollsOffOutcomesOlderThanTheLastHundred() {
        GatewayHealthRegistry registry = new GatewayHealthRegistry();

        for (int i = 0; i < 100; i++) {
            registry.record("MPESA", false);
        }
        assertThat(registry.successRate("MPESA")).isEqualTo(0.0);

        for (int i = 0; i < 100; i++) {
            registry.record("MPESA", true);
        }

        // The 100 failures should have rolled out of the window entirely.
        assertThat(registry.successRate("MPESA")).isEqualTo(1.0);
        assertThat(registry.totalRequests("MPESA")).isEqualTo(200);
    }

    @Test
    void gatewaysAreTrackedIndependently() {
        GatewayHealthRegistry registry = new GatewayHealthRegistry();

        registry.record("MPESA", true);
        registry.record("MPESA", true);
        registry.record("FLUTTERWAVE", false);

        assertThat(registry.successRate("MPESA")).isEqualTo(1.0);
        assertThat(registry.successRate("FLUTTERWAVE")).isEqualTo(0.0);
    }
}
