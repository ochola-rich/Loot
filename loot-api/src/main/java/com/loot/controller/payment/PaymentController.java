package com.loot.controller.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loot.domain.model.EntryPayment;
import com.loot.domain.model.GatewayTransaction;
import com.loot.domain.model.Tournament;
import com.loot.domain.repository.GatewayTransactionRepository;
import com.loot.domain.repository.PaymentRepository;
import com.loot.domain.repository.TournamentRepository;
import com.loot.gateway.CollectionRequest;
import com.loot.gateway.orchestration.CollectionOutcome;
import com.loot.gateway.orchestration.PaymentOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_INITIATED = "INITIATED";
    private static final String STATUS_FAILED = "FAILED";

    private final TournamentRepository tournamentRepository;
    private final PaymentRepository paymentRepository;
    private final GatewayTransactionRepository gatewayTransactionRepository;
    private final PaymentOrchestrator paymentOrchestrator;
    private final PaymentMapper paymentMapper;
    private final ObjectMapper objectMapper;

    public PaymentController(
            TournamentRepository tournamentRepository,
            PaymentRepository paymentRepository,
            GatewayTransactionRepository gatewayTransactionRepository,
            PaymentOrchestrator paymentOrchestrator,
            PaymentMapper paymentMapper,
            ObjectMapper objectMapper) {
        this.tournamentRepository = tournamentRepository;
        this.paymentRepository = paymentRepository;
        this.gatewayTransactionRepository = gatewayTransactionRepository;
        this.paymentOrchestrator = paymentOrchestrator;
        this.paymentMapper = paymentMapper;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Collect entry fee",
            description = "Initiates entry-fee collection for a tournament via the routed payment gateway. "
                    + "Rejects with 404 if the tournament doesn't exist, 409 if it isn't OPEN or is full, "
                    + "and 402 if the gateway declines the charge.")
    @PostMapping("/collect")
    public ResponseEntity<PaymentResponse> collect(
            @Valid @RequestBody CollectPaymentRequest request,
            @Parameter(description = "Client-supplied key so a retried request isn't double-charged; "
                    + "a repeat key is rejected with 409 rather than reprocessed")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader) {

        Optional<Tournament> maybeTournament = tournamentRepository.findById(request.tournamentId());
        if (maybeTournament.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Tournament tournament = maybeTournament.get();

        if (!STATUS_OPEN.equals(tournament.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        long activeEntries = paymentRepository.countByTournamentIdAndStatusNot(tournament.getId(), STATUS_FAILED);
        if (activeEntries >= tournament.getMaxEntries()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        String idempotencyKey = idempotencyKeyHeader != null ? idempotencyKeyHeader : UUID.randomUUID().toString();
        if (gatewayTransactionRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        CollectionRequest collectionRequest = new CollectionRequest(
                UUID.randomUUID().toString(),
                request.playerPhone(),
                tournament.getEntryFeeKes(),
                "KES",
                "Entry fee for tournament " + tournament.getId());

        CollectionOutcome outcome = paymentOrchestrator.processCollection(collectionRequest);

        EntryPayment payment = new EntryPayment();
        payment.setTournamentId(tournament.getId());
        payment.setPlayerPhone(request.playerPhone());
        payment.setAmountKes(tournament.getEntryFeeKes());
        payment.setGateway(outcome.gateway());
        payment.setStatus(outcome.result().isSuccessful() ? STATUS_INITIATED : STATUS_FAILED);
        payment.setMpesaRef(outcome.result().gatewayReference());
        EntryPayment saved = paymentRepository.save(payment);

        recordGatewayTransaction(idempotencyKey, outcome, collectionRequest);

        PaymentResponse response = paymentMapper.toResponse(saved);
        if (!outcome.result().isSuccessful()) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(response);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get payment status by gateway reference")
    @GetMapping("/{reference}/status")
    public ResponseEntity<PaymentResponse> status(@PathVariable String reference) {
        return paymentRepository.findByMpesaRef(reference)
                .map(paymentMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private void recordGatewayTransaction(String idempotencyKey, CollectionOutcome outcome, CollectionRequest request) {
        try {
            GatewayTransaction transaction = new GatewayTransaction();
            transaction.setIdempotencyKey(idempotencyKey);
            transaction.setGateway(outcome.gateway());
            transaction.setRawRequest(objectMapper.writeValueAsString(request));
            transaction.setRawResponse(objectMapper.writeValueAsString(outcome.result()));
            gatewayTransactionRepository.save(transaction);
        } catch (Exception e) {
            // Audit trail failure shouldn't fail the payment itself - the payment
            // outcome has already been decided and persisted above.
            log.error("Failed to record GatewayTransaction for idempotency key {}: {}", idempotencyKey, e.getMessage());
        }
    }
}
