package com.loot.gateway.flutterwave;

import java.util.Map;

/**
 * Builds the Flutterwave mobile money charge request body. Our domain model
 * still has no player email, which Flutterwave's v3 charge API requires
 * even for pure mobile money charges - we synthesize a placeholder from the
 * phone number rather than block on a domain change out of scope here.
 */
public class FlutterwaveChargeRequestFactory {

    private static final Map<String, String> CHARGE_TYPE_BY_CURRENCY = Map.of(
            "KES", "mobile_money_kenya",
            "UGX", "mobile_money_uganda",
            "GHS", "mobile_money_ghana"
    );

    public FlutterwaveChargeRequest build(String transactionId, String phoneNumber, String amount, String currency) {
        return new FlutterwaveChargeRequest(
                transactionId,
                amount,
                currency,
                phoneNumber + "@loot.placeholder",
                phoneNumber,
                "Loot Player"
        );
    }

    /**
     * Returns null for currencies the plan never gave us a confirmed
     * ?type= value for (TZS) - callers must treat that as unsupported
     * rather than guess an endpoint that might misroute a real charge.
     */
    public String chargeTypeFor(String currency) {
        return CHARGE_TYPE_BY_CURRENCY.get(currency);
    }
}
