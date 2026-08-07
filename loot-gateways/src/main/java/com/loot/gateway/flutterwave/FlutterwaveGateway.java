package com.loot.gateway.flutterwave;

import com.loot.domain.money.CurrencyGatewaySupport;
import com.loot.gateway.CollectionRequest;
import com.loot.gateway.CollectionResult;
import com.loot.gateway.DisbursalRequest;
import com.loot.gateway.DisbursalResult;
import com.loot.gateway.GatewayHttpClients;
import com.loot.gateway.PaymentGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
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
    @Retryable(retryFor = HttpServerErrorException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2))
    public CollectionResult initiateCollection(CollectionRequest req) {
        if (!CurrencyGatewaySupport.isSupported(req.currency(), "FLUTTERWAVE")) {
            return new CollectionResult(false, null, "Flutterwave does not support currency " + req.currency());
        }
        String chargeType = chargeRequestFactory.chargeTypeFor(req.currency());
        if (chargeType == null) {
            return new CollectionResult(false, null,
                    "No Flutterwave mobile money charge type known for currency " + req.currency());
        }

        String amount = req.amount().toPlainString();
        FlutterwaveChargeRequest chargeRequest = chargeRequestFactory.build(
                req.transactionId(), req.playerPhone(), amount, req.currency());

        FlutterwaveChargeResponse response;
        try {
            response = restClient.post()
                    .uri("/v3/charges?type=" + chargeType)
                    .header("Authorization", "Bearer " + secretKey)
                    .body(chargeRequest)
                    .retrieve()
                    .body(FlutterwaveChargeResponse.class);
        } catch (HttpServerErrorException e) {
            throw e; // let @Retryable catch and retry this
        } catch (Exception e) {
            return new CollectionResult(false, null, "Flutterwave charge request failed: " + e.getMessage());
        }

        if (response == null || response.data() == null) {
            return new CollectionResult(false, null, "Empty response from Flutterwave");
        }

        boolean accepted = "success".equals(response.status());
        return new CollectionResult(accepted, response.data().flwRef(), response.message());
    }

    @Recover
    public CollectionResult recoverCollection(HttpServerErrorException e, CollectionRequest req) {
        return new CollectionResult(false, null, "Flutterwave charge failed after retries: " + e.getMessage());
    }

    @Override
    @Retryable(retryFor = HttpServerErrorException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2))
    public DisbursalResult initiatePayout(DisbursalRequest req) {
        if (!"KES".equals(req.currency())) {
            // Only KES has a confirmed Flutterwave bank code (MPS) - see
            // FlutterwaveTransferRequestFactory for why the others aren't wired.
            return new DisbursalResult(false, null,
                    "Flutterwave payouts only support KES currently, got " + req.currency());
        }

        String amount = req.amount().toPlainString();
        FlutterwaveTransferRequest transferRequest = transferRequestFactory.build(
                req.transactionId(), req.recipientPhone(), amount, req.currency(), req.description());

        FlutterwaveTransferResponse response;
        try {
            response = restClient.post()
                    .uri("/v3/transfers")
                    .header("Authorization", "Bearer " + secretKey)
                    .body(transferRequest)
                    .retrieve()
                    .body(FlutterwaveTransferResponse.class);
        } catch (HttpServerErrorException e) {
            throw e;
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

    @Recover
    public DisbursalResult recoverPayout(HttpServerErrorException e, DisbursalRequest req) {
        return new DisbursalResult(false, null, "Flutterwave transfer failed after retries: " + e.getMessage());
    }
}
