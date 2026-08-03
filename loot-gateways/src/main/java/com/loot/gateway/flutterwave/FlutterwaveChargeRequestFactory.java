package com.loot.gateway.flutterwave;

/**
 * Builds the Flutterwave mobile money charge request body. Hardcoded to
 * KES/mobile_money_kenya for now: CollectionRequest has no currency field
 * yet (that lands in t28's Money value object), and neither does our domain
 * model have a player email, which Flutterwave's v3 charge API requires
 * even for pure mobile money charges - we synthesize a placeholder from the
 * phone number rather than block on a domain change out of scope here.
 */
public class FlutterwaveChargeRequestFactory {

    static final String CHARGE_TYPE = "mobile_money_kenya";
    private static final String CURRENCY = "KES";

    public FlutterwaveChargeRequest build(String transactionId, String phoneNumber, String amount) {
        return new FlutterwaveChargeRequest(
                transactionId,
                amount,
                CURRENCY,
                phoneNumber + "@loot.placeholder",
                phoneNumber,
                "Loot Player"
        );
    }
}
