package com.loot.gateway.orchestration;

import com.loot.gateway.CollectionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class CountryBasedRoutingStrategyTest {

    @Mock
    GatewayHealthRegistry healthRegistry;

    CountryBasedRoutingStrategy strategy;

    @Test
    void routesKenyaToMpesaWithFlutterwaveFallback() {
        allGatewaysHealthy();
        strategy = new CountryBasedRoutingStrategy(healthRegistry);
        CollectionRequest req = request("254712345678");

        assertThat(strategy.selectGateway(req)).isEqualTo("MPESA");
        assertThat(strategy.selectFallback(req, "MPESA")).isEqualTo("FLUTTERWAVE");
    }

    @Test
    void routesUgandaToFlutterwaveWithNoFallback() {
        allGatewaysHealthy();
        strategy = new CountryBasedRoutingStrategy(healthRegistry);
        CollectionRequest req = request("256712345678");

        assertThat(strategy.selectGateway(req)).isEqualTo("FLUTTERWAVE");
        assertThat(strategy.selectFallback(req, "FLUTTERWAVE")).isNull();
    }

    @Test
    void routesGhanaToFlutterwaveWithNoFallback() {
        allGatewaysHealthy();
        strategy = new CountryBasedRoutingStrategy(healthRegistry);
        CollectionRequest req = request("233712345678");

        assertThat(strategy.selectGateway(req)).isEqualTo("FLUTTERWAVE");
    }

    @Test
    void unknownCountryDefaultsToFlutterwave() {
        allGatewaysHealthy();
        strategy = new CountryBasedRoutingStrategy(healthRegistry);
        CollectionRequest req = request("447712345678");

        assertThat(strategy.selectGateway(req)).isEqualTo("FLUTTERWAVE");
    }

    @Test
    void routesToFallbackWhenPreferredGatewayIsUnhealthy() {
        lenient().when(healthRegistry.isHealthy("MPESA")).thenReturn(false);
        lenient().when(healthRegistry.isHealthy("FLUTTERWAVE")).thenReturn(true);
        strategy = new CountryBasedRoutingStrategy(healthRegistry);
        CollectionRequest req = request("254712345678");

        assertThat(strategy.selectGateway(req)).isEqualTo("FLUTTERWAVE");
    }

    private void allGatewaysHealthy() {
        lenient().when(healthRegistry.isHealthy("MPESA")).thenReturn(true);
        lenient().when(healthRegistry.isHealthy("FLUTTERWAVE")).thenReturn(true);
    }

    private CollectionRequest request(String phone) {
        return new CollectionRequest("txn-1", phone, BigDecimal.TEN, "KES", "test");
    }
}
