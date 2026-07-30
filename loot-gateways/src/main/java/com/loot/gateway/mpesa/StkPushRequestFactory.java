package com.loot.gateway.mpesa;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Builds the Daraja STK Push request body. Kept separate from MpesaGateway
 * so the field-mapping/password/timestamp logic can be unit tested against
 * known inputs without touching HTTP.
 */
public class StkPushRequestFactory {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final String shortcode;
    private final String passkey;
    private final String callbackBaseUrl;

    public StkPushRequestFactory(String shortcode, String passkey, String callbackBaseUrl) {
        this.shortcode = shortcode;
        this.passkey = passkey;
        this.callbackBaseUrl = callbackBaseUrl;
    }

    public StkPushRequest build(String phoneNumber, String amount, String accountReference, String transactionDesc) {
        return build(phoneNumber, amount, accountReference, transactionDesc, LocalDateTime.now());
    }

    StkPushRequest build(String phoneNumber, String amount, String accountReference, String transactionDesc,
                          LocalDateTime now) {
        String timestamp = now.format(TIMESTAMP_FORMAT);
        String password = Base64.getEncoder().encodeToString((shortcode + passkey + timestamp).getBytes());

        return new StkPushRequest(
                shortcode,
                password,
                timestamp,
                "CustomerPayBillOnline",
                amount,
                phoneNumber,
                shortcode,
                phoneNumber,
                callbackBaseUrl + "/api/v1/webhooks/mpesa/confirmation",
                accountReference,
                transactionDesc
        );
    }
}
