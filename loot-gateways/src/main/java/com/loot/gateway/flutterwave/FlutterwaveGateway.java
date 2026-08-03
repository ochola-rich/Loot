package com.loot.gateway.flutterwave;

import com.loot.gateway.CollectionRequest;
import com.loot.gateway.CollectionResult;
import com.loot.gateway.DisbursalRequest;
import com.loot.gateway.DisbursalResult;
import com.loot.gateway.GatewayHttpClients;
import com.loot.gateway.PaymentGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component("flutterwaveGateway")
public class FlutterwaveGateway implements PaymentGateway {

    private final RestClient restClient;
    private final String secretKey;
    private final FlutterwaveChargeRequestFactory chargeRequestFactory = new FlutterwaveChargeRequestFactory();

    public FlutterwaveGateway(
            @Value("${flutterwave.base-url:https://api.flutterwave.com}") String baseUrl,
            @Value("${flutterwave.secret-key}") String secretKey) {
        this.restClient = GatewayHttpClients.restClient(baseUrl);
        this.secretKey = secretKey;
    }

    @Override
    public CollectionResult initiateCollection(CollectionRequest req) {
        String amount = req.amount().toPlainString();
        FlutterwaveChargeRequest chargeRequest = chargeRequestFactory.build(
                req.transactionId(), req.playerPhone(), amount);

        FlutterwaveChargeResponse response;
        try {
            response = restClient.post()
                    .uri("/v3/charges?type=" + FlutterwaveChargeRequestFactory.CHARGE_TYPE)
                    .header("Authorization", "Bearer " + secretKey)
                    .body(chargeRequest)
                    .retrieve()
                    .body(FlutterwaveChargeResponse.class);
        } catch (Exception e) {
            return new CollectionResult(false, null, "Flutterwave charge request failed: " + e.getMessage());
        }

        if (response == null || response.data() == null) {
            return new CollectionResult(false, null, "Empty response from Flutterwave");
        }

        boolean accepted = "success".equals(response.status());
        return new CollectionResult(accepted, response.data().flwRef(), response.message());
    }

    @Override
    public DisbursalResult initiatePayout(DisbursalRequest req) {
        // Implemented in t25 (FlutterwaveTransferAdapter) - still a stub for now.
        return new DisbursalResult(true, "flw_tx_" + UUID.randomUUID().toString().substring(0, 8), "Transfer Initiated");
    }
}
