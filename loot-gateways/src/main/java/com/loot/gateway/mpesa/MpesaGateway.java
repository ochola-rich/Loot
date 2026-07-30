package com.loot.gateway.mpesa;

import com.loot.gateway.CollectionRequest;
import com.loot.gateway.CollectionResult;
import com.loot.gateway.DisbursalRequest;
import com.loot.gateway.DisbursalResult;
import com.loot.gateway.PaymentGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.RoundingMode;
import java.util.UUID;

@Component("mpesaGateway")
public class MpesaGateway implements PaymentGateway {

    private final DarajaAuthService authService;
    private final StkPushRequestFactory stkPushRequestFactory;
    private final RestClient restClient;

    public MpesaGateway(
            DarajaAuthService authService,
            @Value("${daraja.base-url:https://sandbox.safaricom.co.ke}") String baseUrl,
            @Value("${daraja.shortcode:174379}") String shortcode,
            @Value("${daraja.passkey}") String passkey,
            @Value("${daraja.callback-base-url:http://localhost:8080}") String callbackBaseUrl) {
        this.authService = authService;
        this.stkPushRequestFactory = new StkPushRequestFactory(shortcode, passkey, callbackBaseUrl);
        this.restClient = RestClient.create(baseUrl);
    }

    @Override
    public CollectionResult initiateCollection(CollectionRequest req) {
        String amount = req.amount().setScale(0, RoundingMode.HALF_UP).toPlainString();
        StkPushRequest stkRequest = stkPushRequestFactory.build(
                req.playerPhone(), amount, req.transactionId(), req.description());

        StkPushResponse response;
        try {
            response = restClient.post()
                    .uri("/mpesa/stkpush/v1/processrequest")
                    .header("Authorization", "Bearer " + authService.getValidToken())
                    .body(stkRequest)
                    .retrieve()
                    .body(StkPushResponse.class);
        } catch (Exception e) {
            return new CollectionResult(false, null, "STK Push request failed: " + e.getMessage());
        }

        if (response == null) {
            return new CollectionResult(false, null, "Empty response from Daraja");
        }

        boolean accepted = "0".equals(response.responseCode());
        return new CollectionResult(accepted, response.checkoutRequestId(), response.responseDescription());
    }

    @Override
    public DisbursalResult initiatePayout(DisbursalRequest req) {
        // Implemented in MpesaB2CAdapter work (t19) - still a stub for now.
        return new DisbursalResult(true, "b2c_" + UUID.randomUUID().toString().substring(0, 8), "Payout Initiated");
    }
}
