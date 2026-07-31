package com.loot.controller.webhook;

import com.loot.domain.model.EntryPayment;
import com.loot.domain.model.PrizeDisbursal;
import com.loot.domain.repository.DisbursalRepository;
import com.loot.domain.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks/mpesa")
public class MpesaWebhookController {

    private static final Logger log = LoggerFactory.getLogger(MpesaWebhookController.class);

    private final PaymentRepository paymentRepository;
    private final DisbursalRepository disbursalRepository;

    public MpesaWebhookController(PaymentRepository paymentRepository, DisbursalRepository disbursalRepository) {
        this.paymentRepository = paymentRepository;
        this.disbursalRepository = disbursalRepository;
    }

    @PostMapping("/confirmation")
    public DarajaAckResponse confirmation(@RequestBody StkCallbackEnvelope envelope) {
        StkCallback callback = envelope.body().stkCallback();

        paymentRepository.findByMpesaRef(callback.checkoutRequestId()).ifPresentOrElse(
                payment -> updatePaymentStatus(payment, callback),
                () -> log.warn("No EntryPayment found for CheckoutRequestID {}", callback.checkoutRequestId())
        );

        // Daraja requires this exact ack shape regardless of whether we matched a payment,
        // otherwise it will keep retrying the callback.
        return DarajaAckResponse.accepted();
    }

    @PostMapping("/result")
    public DarajaAckResponse result(@RequestBody B2CResultEnvelope envelope) {
        B2CResultEnvelope.Result result = envelope.result();

        disbursalRepository.findByGatewayRef(result.conversationId()).ifPresentOrElse(
                disbursal -> updateDisbursalStatus(disbursal, result.resultCode() == 0 ? "CONFIRMED" : "FAILED"),
                () -> log.warn("No PrizeDisbursal found for ConversationID {}", result.conversationId())
        );

        return DarajaAckResponse.accepted();
    }

    @PostMapping("/timeout")
    public DarajaAckResponse timeout(@RequestBody B2CResultEnvelope envelope) {
        B2CResultEnvelope.Result result = envelope.result();

        // No retry dispatch here yet - that's orchestration-level work (fallback/retry
        // land in t32/t34). For now a timeout just gets recorded so it's visible and
        // can be retried manually or picked up by that orchestration once it exists.
        disbursalRepository.findByGatewayRef(result.conversationId()).ifPresentOrElse(
                disbursal -> updateDisbursalStatus(disbursal, "TIMEOUT"),
                () -> log.warn("No PrizeDisbursal found for ConversationID {} (timeout callback)",
                        result.conversationId())
        );

        return DarajaAckResponse.accepted();
    }

    private void updatePaymentStatus(EntryPayment payment, StkCallback callback) {
        payment.setStatus(callback.resultCode() == 0 ? "CONFIRMED" : "FAILED");
        paymentRepository.save(payment);
    }

    private void updateDisbursalStatus(PrizeDisbursal disbursal, String status) {
        disbursal.setStatus(status);
        disbursalRepository.save(disbursal);
    }
}
