package com.loot.controller.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loot.domain.model.EntryPayment;
import com.loot.domain.model.PrizeDisbursal;
import com.loot.domain.model.WebhookEvent;
import com.loot.domain.repository.DisbursalRepository;
import com.loot.domain.repository.PaymentRepository;
import com.loot.domain.repository.WebhookEventRepository;
import com.loot.gateway.mpesa.DarajaStatusMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/webhooks/mpesa")
public class MpesaWebhookController {

    private static final Logger log = LoggerFactory.getLogger(MpesaWebhookController.class);

    private final PaymentRepository paymentRepository;
    private final DisbursalRepository disbursalRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final ObjectMapper objectMapper;

    public MpesaWebhookController(
            PaymentRepository paymentRepository,
            DisbursalRepository disbursalRepository,
            WebhookEventRepository webhookEventRepository,
            ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.disbursalRepository = disbursalRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/confirmation")
    public DarajaAckResponse confirmation(@RequestBody String rawBody) throws Exception {
        StkCallbackEnvelope envelope = objectMapper.readValue(rawBody, StkCallbackEnvelope.class);
        StkCallback callback = envelope.body().stkCallback();

        paymentRepository.findByMpesaRef(callback.checkoutRequestId()).ifPresentOrElse(
                payment -> updatePaymentStatus(payment, callback),
                () -> log.warn("No EntryPayment found for CheckoutRequestID {}", callback.checkoutRequestId())
        );

        // Daraja requires this exact ack shape regardless of whether we matched a payment,
        // otherwise it will keep retrying the callback.
        recordEvent("C2B_CONFIRMATION", rawBody);
        return DarajaAckResponse.accepted();
    }

    @PostMapping("/result")
    public DarajaAckResponse result(@RequestBody String rawBody) throws Exception {
        B2CResultEnvelope envelope = objectMapper.readValue(rawBody, B2CResultEnvelope.class);
        B2CResultEnvelope.Result result = envelope.result();

        disbursalRepository.findByGatewayRef(result.conversationId()).ifPresentOrElse(
                disbursal -> updateDisbursalStatus(disbursal, result.resultCode() == 0 ? "CONFIRMED" : "FAILED"),
                () -> log.warn("No PrizeDisbursal found for ConversationID {}", result.conversationId())
        );

        recordEvent("B2C_RESULT", rawBody);
        return DarajaAckResponse.accepted();
    }

    @PostMapping("/timeout")
    public DarajaAckResponse timeout(@RequestBody String rawBody) throws Exception {
        B2CResultEnvelope envelope = objectMapper.readValue(rawBody, B2CResultEnvelope.class);
        B2CResultEnvelope.Result result = envelope.result();

        // No retry dispatch here yet - that's orchestration-level work (fallback/retry
        // land in t32/t34). For now a timeout just gets recorded so it's visible and
        // can be retried manually or picked up by that orchestration once it exists.
        disbursalRepository.findByGatewayRef(result.conversationId()).ifPresentOrElse(
                disbursal -> updateDisbursalStatus(disbursal, "TIMEOUT"),
                () -> log.warn("No PrizeDisbursal found for ConversationID {} (timeout callback)",
                        result.conversationId())
        );

        recordEvent("B2C_TIMEOUT", rawBody);
        return DarajaAckResponse.accepted();
    }

    private void updatePaymentStatus(EntryPayment payment, StkCallback callback) {
        payment.setStatus(DarajaStatusMapper.toPaymentStatus(callback.resultCode()).name());
        paymentRepository.save(payment);
    }

    private void updateDisbursalStatus(PrizeDisbursal disbursal, String status) {
        disbursal.setStatus(status);
        disbursalRepository.save(disbursal);
    }

    private void recordEvent(String eventType, String rawBody) {
        WebhookEvent event = new WebhookEvent();
        event.setGateway("MPESA");
        event.setEventType(eventType);
        event.setRequestBody(rawBody);
        event.setResponseBody(null);
        event.setStatus("RECEIVED");
        event.setProcessedAt(Instant.now());
        webhookEventRepository.save(event);
    }
}
