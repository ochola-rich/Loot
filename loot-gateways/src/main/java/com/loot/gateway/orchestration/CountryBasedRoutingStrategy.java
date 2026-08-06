package com.loot.gateway.orchestration;

import com.loot.gateway.CollectionRequest;
import org.springframework.stereotype.Component;

/**
 * Kenya routes to M-Pesa first with Flutterwave as fallback; Uganda, Ghana
 * and Tanzania only have Flutterwave (Daraja is Kenya-only, see
 * CurrencyGatewaySupport), so there's no fallback to offer them - if
 * Flutterwave fails there's nowhere else to send the request.
 */
@Component
public class CountryBasedRoutingStrategy implements GatewayRoutingStrategy {

    private final GatewayHealthRegistry healthRegistry;

    public CountryBasedRoutingStrategy(GatewayHealthRegistry healthRegistry) {
        this.healthRegistry = healthRegistry;
    }

    @Override
    public String selectGateway(CollectionRequest req) {
        String preferred = defaultGatewayFor(countryFor(req.playerPhone()));
        String fallback = selectFallback(req, preferred);

        if (!healthRegistry.isHealthy(preferred) && fallback != null && healthRegistry.isHealthy(fallback)) {
            return fallback;
        }
        return preferred;
    }

    @Override
    public String selectFallback(CollectionRequest req, String primaryGateway) {
        if ("KE".equals(countryFor(req.playerPhone())) && "MPESA".equals(primaryGateway)) {
            return "FLUTTERWAVE";
        }
        return null;
    }

    private String defaultGatewayFor(String country) {
        return "KE".equals(country) ? "MPESA" : "FLUTTERWAVE";
    }

    private String countryFor(String phone) {
        String normalized = phone.startsWith("+") ? phone.substring(1) : phone;
        if (normalized.startsWith("254")) return "KE";
        if (normalized.startsWith("256")) return "UG";
        if (normalized.startsWith("233")) return "GH";
        if (normalized.startsWith("255")) return "TZ";
        return "UNKNOWN";
    }
}
