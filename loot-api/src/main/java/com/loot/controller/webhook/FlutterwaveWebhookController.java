package com.loot.controller.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loot.domain.model.EntryPayment;
import com.loot.domain.model.PrizeDisbursal;
import com.loot.domain.repository.DisbursalRepository;
import com.loot.domain.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Flutterwave signs webhooks by simply echoing back whatever secret hash
 * string you configured in their dashboard, in the verif-hash header - no
 * HMAC involved. We compare it to our configured secret with
 * MessageDigest.isEqual so the comparison runs in constant time regardless
 * of where the strings first differ.
 */
@RestController
@RequestMapping("/api/v1/webhooks/flutterwave")
public class FlutterwaveWebhookController {

    private static final Logger log = LoggerFactory.getLogger(FlutterwaveWebhookController.class);

    private final PaymentRepository paymentRepository;
    private final DisbursalRepository disbursalRepository;
    private final ObjectMapper objectMapper;
    private final String webhookSecretHash;

    public FlutterwaveWebhookController(
            PaymentRepository paymentRepository,
            DisbursalRepository disbursalRepository,
            ObjectMapper objectMapper,
            @Value("${flutterwave.webhook-secret-hash}") String webhookSecretHash) {
        this.paymentRepository = paymentRepository;
        this.disbursalRepository = disbursalRepository;
        this.objectMapper = objectMapper;
        this.webhookSecretHash = webhookSecretHash;
    }

    @PostMapping
    public ResponseEntity<Void> handle(
            @RequestHeader(value = "verif-hash", required = false) String verifHash,
            @RequestBody String rawBody) throws Exception {

        if (verifHash == null || !constantTimeEquals(verifHash, webhookSecretHash)) {
            log.warn("Rejected Flutterwave webhook with invalid or missing verif-hash");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        FlutterwaveWebhookEvent event = objectMapper.readValue(rawBody, FlutterwaveWebhookEvent.class);
        String eventType = event.event() == null ? "" : event.event();

        if (eventType.startsWith("charge.")) {
            handleCharge(event.data());
        } else if (eventType.startsWith("transfer.")) {
            handleTransfer(event.data());
        } else {
            log.warn("Unhandled Flutterwave event type: {}", eventType);
        }

        return ResponseEntity.ok().build();
    }

    private void handleCharge(FlutterwaveWebhookEvent.Data data) {
        if (data == null || data.flwRef() == null) {
            return;
        }
        paymentRepository.findByMpesaRef(data.flwRef()).ifPresentOrElse(
                payment -> updatePaymentStatus(payment, data.status()),
                () -> log.warn("No EntryPayment found for flw_ref {}", data.flwRef())
        );
    }

    private void handleTransfer(FlutterwaveWebhookEvent.Data data) {
        if (data == null || data.id() == null) {
            return;
        }
        String reference = String.valueOf(data.id());
        disbursalRepository.findByGatewayRef(reference).ifPresentOrElse(
                disbursal -> updateDisbursalStatus(disbursal, data.status()),
                () -> log.warn("No PrizeDisbursal found for transfer id {}", reference)
        );
    }

    private void updatePaymentStatus(EntryPayment payment, String flwStatus) {
        payment.setStatus(mapStatus(flwStatus));
        paymentRepository.save(payment);
    }

    private void updateDisbursalStatus(PrizeDisbursal disbursal, String flwStatus) {
        disbursal.setStatus(mapStatus(flwStatus));
        disbursalRepository.save(disbursal);
    }

    // Placeholder mapping - t27 replaces this (and Daraja's inline ResultCode
    // checks) with a shared PaymentStatus enum + dedicated status mappers.
    private String mapStatus(String flwStatus) {
        if (flwStatus == null) {
            return "PENDING";
        }
        return switch (flwStatus.toLowerCase()) {
            case "successful" -> "CONFIRMED";
            case "failed" -> "FAILED";
            default -> "PENDING";
        };
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
