package com.loot.gateway.mpesa;

/**
 * Builds the Daraja B2C payment request body. securityCredential is taken
 * as-is from config - it must already be the initiator password encrypted
 * with Safaricom's public certificate (done once, offline, per their docs).
 * We don't do that RSA encryption in-app since it depends on a certificate
 * file this repo doesn't ship.
 */
public class B2CRequestFactory {

    private static final String COMMAND_ID = "BusinessPayment";

    private final String initiatorName;
    private final String securityCredential;
    private final String shortcode;
    private final String callbackBaseUrl;

    public B2CRequestFactory(String initiatorName, String securityCredential, String shortcode,
                              String callbackBaseUrl) {
        this.initiatorName = initiatorName;
        this.securityCredential = securityCredential;
        this.shortcode = shortcode;
        this.callbackBaseUrl = callbackBaseUrl;
    }

    public B2CRequest build(String recipientPhone, String amount, String remarks) {
        return new B2CRequest(
                initiatorName,
                securityCredential,
                COMMAND_ID,
                amount,
                shortcode,
                recipientPhone,
                remarks,
                callbackBaseUrl + "/api/v1/webhooks/mpesa/timeout",
                callbackBaseUrl + "/api/v1/webhooks/mpesa/result",
                remarks
        );
    }
}
