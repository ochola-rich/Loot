package com.loot.controller.webhook;

import com.loot.domain.model.EntryPayment;
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

    public MpesaWebhookController(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @PostMapping("/confirmation")
    public DarajaAckResponse confirmation(@RequestBody StkCallbackEnvelope envelope) {
        StkCallback callback = envelope.body().stkCallback();

        paymentRepository.findByMpesaRef(callback.checkoutRequestId()).ifPresentOrElse(
                payment -> updateStatus(payment, callback),
                () -> log.warn("No EntryPayment found for CheckoutRequestID {}", callback.checkoutRequestId())
        );

        // Daraja requires this exact ack shape regardless of whether we matched a payment,
        // otherwise it will keep retrying the callback.
        return DarajaAckResponse.accepted();
    }

    private void updateStatus(EntryPayment payment, StkCallback callback) {
        payment.setStatus(callback.resultCode() == 0 ? "CONFIRMED" : "FAILED");
        paymentRepository.save(payment);
    }
}
