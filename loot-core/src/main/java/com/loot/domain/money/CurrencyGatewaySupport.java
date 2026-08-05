package com.loot.domain.money;

import java.util.Map;
import java.util.Set;

/**
 * Which gateway can move money in which currency. KES is the only currency
 * M-Pesa (Daraja) can touch at all - it's a Kenya-only product. Flutterwave
 * covers all four currencies Loot supports.
 */
public final class CurrencyGatewaySupport {

    private static final Map<String, Set<String>> SUPPORTED_GATEWAYS_BY_CURRENCY = Map.of(
            "KES", Set.of("MPESA", "FLUTTERWAVE"),
            "UGX", Set.of("FLUTTERWAVE"),
            "GHS", Set.of("FLUTTERWAVE"),
            "TZS", Set.of("FLUTTERWAVE")
    );

    private CurrencyGatewaySupport() {}

    public static boolean isSupported(String currency, String gateway) {
        Set<String> gateways = SUPPORTED_GATEWAYS_BY_CURRENCY.get(currency);
        return gateways != null && gateways.contains(gateway);
    }
}
