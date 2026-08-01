package com.loot.gateway.mpesa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loot.domain.model.WebhookEvent;
import com.loot.domain.repository.WebhookEventRepository;
import com.loot.gateway.CollectionRequest;
import com.loot.gateway.CollectionResult;
import com.loot.gateway.DisbursalRequest;
import com.loot.gateway.DisbursalResult;
import com.loot.gateway.PaymentGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.RoundingMode;
import java.time.Instant;

@Component("mpesaGateway")
public class MpesaGateway implements PaymentGateway {

    private final DarajaAuthService authService;
    private final StkPushRequestFactory stkPushRequestFactory;
    private final B2CRequestFactory b2cRequestFactory;
    private final RestClient restClient;
    private final WebhookEventRepository webhookEventRepository;
    private final ObjectMapper objectMapper;

    public MpesaGateway(
            DarajaAuthService authService,
            @Value("${daraja.base-url:https://sandbox.safaricom.co.ke}") String baseUrl,
            @Value("${daraja.shortcode:174379}") String shortcode,
            @Value("${daraja.passkey}") String passkey,
            @Value("${daraja.callback-base-url:http://localhost:8080}") String callbackBaseUrl,
            @Value("${daraja.initiator-name}") String initiatorName,
            @Value("${daraja.security-credential}") String securityCredential,
            WebhookEventRepository webhookEventRepository,
            ObjectMapper objectMapper) {
        this.authService = authService;
        this.stkPushRequestFactory = new StkPushRequestFactory(shortcode, passkey, callbackBaseUrl);
        this.b2cRequestFactory = new B2CRequestFactory(initiatorName, securityCredential, shortcode, callbackBaseUrl);
        this.restClient = RestClient.create(baseUrl);
        this.webhookEventRepository = webhookEventRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public CollectionResult initiateCollection(CollectionRequest req) {
        String amount = req.amount().setScale(0, RoundingMode.HALF_UP).toPlainString();
        StkPushRequest stkRequest = stkPushRequestFactory.build(
                req.playerPhone(), amount, req.transactionId(), req.description());

        StkPushResponse response = null;
        String error = null;
        try {
            response = restClient.post()
                    .uri("/mpesa/stkpush/v1/processrequest")
                    .header("Authorization", "Bearer " + authService.getValidToken())
                    .body(stkRequest)
                    .retrieve()
                    .body(StkPushResponse.class);
        } catch (Exception e) {
            error = e.getMessage();
        }

        recordEvent("STK_PUSH_REQUEST", stkRequest, response, error);

        if (error != null) {
            return new CollectionResult(false, null, "STK Push request failed: " + error);
        }
        if (response == null) {
            return new CollectionResult(false, null, "Empty response from Daraja");
        }

        boolean accepted = "0".equals(response.responseCode());
        return new CollectionResult(accepted, response.checkoutRequestId(), response.responseDescription());
    }

    @Override
    public DisbursalResult initiatePayout(DisbursalRequest req) {
        String amount = req.amount().setScale(0, RoundingMode.HALF_UP).toPlainString();
        B2CRequest b2cRequest = b2cRequestFactory.build(req.recipientPhone(), amount, req.description());

        B2CResponse response = null;
        String error = null;
        try {
            response = restClient.post()
                    .uri("/mpesa/b2c/v3/paymentrequest")
                    .header("Authorization", "Bearer " + authService.getValidToken())
                    .body(b2cRequest)
                    .retrieve()
                    .body(B2CResponse.class);
        } catch (Exception e) {
            error = e.getMessage();
        }

        recordEvent("B2C_PAYOUT_REQUEST", b2cRequest, response, error);

        if (error != null) {
            return new DisbursalResult(false, null, "B2C payout request failed: " + error);
        }
        if (response == null) {
            return new DisbursalResult(false, null, "Empty response from Daraja");
        }

        boolean accepted = "0".equals(response.responseCode());
        return new DisbursalResult(accepted, response.conversationId(), response.responseDescription());
    }

    private void recordEvent(String eventType, Object request, Object response, String error) {
        WebhookEvent event = new WebhookEvent();
        event.setGateway("MPESA");
        event.setEventType(eventType);
        event.setRequestBody(toJson(request));
        event.setResponseBody(error != null ? "{\"error\":\"" + error + "\"}" : toJson(response));
        event.setStatus(error != null ? "FAILED" : "SENT");
        event.setProcessedAt(Instant.now());
        webhookEventRepository.save(event);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"failed to serialize\"}";
        }
    }
}
