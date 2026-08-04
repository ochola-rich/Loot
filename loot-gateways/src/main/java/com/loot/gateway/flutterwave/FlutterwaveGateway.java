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

@Component("flutterwaveGateway")
public class FlutterwaveGateway implements PaymentGateway {

    private final RestClient restClient;
    private final String secretKey;
    private final FlutterwaveChargeRequestFactory chargeRequestFactory = new FlutterwaveChargeRequestFactory();
    private final FlutterwaveTransferRequestFactory transferRequestFactory = new FlutterwaveTransferRequestFactory();

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
        String amount = req.amount().toPlainString();
        FlutterwaveTransferRequest transferRequest = transferRequestFactory.build(
                req.transactionId(), req.recipientPhone(), amount, req.description());

        FlutterwaveTransferResponse response;
        try {
            response = restClient.post()
                    .uri("/v3/transfers")
                    .header("Authorization", "Bearer " + secretKey)
                    .body(transferRequest)
                    .retrieve()
                    .body(FlutterwaveTransferResponse.class);
        } catch (Exception e) {
            return new DisbursalResult(false, null, "Flutterwave transfer request failed: " + e.getMessage());
        }

        if (response == null || response.data() == null) {
            return new DisbursalResult(false, null, "Empty response from Flutterwave");
        }

        boolean accepted = "success".equals(response.status());
        String reference = response.data().id() != null ? String.valueOf(response.data().id()) : null;
        return new DisbursalResult(accepted, reference, response.message());
    }
}
