package com.loot.controller.disbursal;

import com.loot.domain.model.PrizeDisbursal;
import com.loot.domain.model.Tournament;
import com.loot.domain.repository.DisbursalRepository;
import com.loot.domain.repository.TournamentRepository;
import com.loot.gateway.DisbursalRequest;
import com.loot.gateway.orchestration.DisbursalOutcome;
import com.loot.gateway.orchestration.PaymentOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/disbursals")
@Tag(name = "Disbursals")
public class DisbursalController {

    private static final String STATUS_CLOSED = "CLOSED";
    private static final String STATUS_DISBURSED = "DISBURSED";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_FAILED = "FAILED";

    private final TournamentRepository tournamentRepository;
    private final DisbursalRepository disbursalRepository;
    private final PaymentOrchestrator paymentOrchestrator;
    private final DisbursalMapper disbursalMapper;

    public DisbursalController(
            TournamentRepository tournamentRepository,
            DisbursalRepository disbursalRepository,
            PaymentOrchestrator paymentOrchestrator,
            DisbursalMapper disbursalMapper) {
        this.tournamentRepository = tournamentRepository;
        this.disbursalRepository = disbursalRepository;
        this.paymentOrchestrator = paymentOrchestrator;
        this.disbursalMapper = disbursalMapper;
    }

    @Operation(summary = "Trigger a single prize payout",
            description = "Pays one winner. Rejects with 409 unless the tournament is CLOSED.")
    @PostMapping("/trigger")
    public ResponseEntity<DisbursalResponse> trigger(@Valid @RequestBody TriggerDisbursalRequest request) {
        Optional<ResponseEntity<DisbursalResponse>> rejection = rejectUnlessClosed(request.tournamentId());
        if (rejection.isPresent()) {
            return rejection.get();
        }
        Tournament tournament = tournamentRepository.findById(request.tournamentId()).orElseThrow();

        DisbursalRequest payoutRequest = toDisbursalRequest(tournament, request.winner());
        DisbursalOutcome outcome = paymentOrchestrator.processPayout(payoutRequest);
        PrizeDisbursal saved = save(tournament, request.winner(), outcome);

        DisbursalResponse response = disbursalMapper.toResponse(saved);
        if (!outcome.result().isSuccessful()) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(response);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Bulk prize payout",
            description = "Pays a list of winners concurrently and marks the tournament DISBURSED once the run "
                    + "completes, regardless of individual per-winner outcome. Rejects with 409 unless the "
                    + "tournament is CLOSED.")
    @PostMapping("/bulk")
    public ResponseEntity<List<DisbursalResponse>> bulk(@Valid @RequestBody BulkDisbursalRequest request) {
        Optional<ResponseEntity<DisbursalResponse>> rejection = rejectUnlessClosed(request.tournamentId());
        if (rejection.isPresent()) {
            return ResponseEntity.status(rejection.get().getStatusCode()).build();
        }
        Tournament tournament = tournamentRepository.findById(request.tournamentId()).orElseThrow();

        List<DisbursalRequest> payoutRequests = request.winners().stream()
                .map(winner -> toDisbursalRequest(tournament, winner))
                .toList();
        List<DisbursalOutcome> outcomes = paymentOrchestrator.processBulkPayout(payoutRequests);

        List<DisbursalResponse> responses = new ArrayList<>(outcomes.size());
        for (int i = 0; i < outcomes.size(); i++) {
            PrizeDisbursal saved = save(tournament, request.winners().get(i), outcomes.get(i));
            responses.add(disbursalMapper.toResponse(saved));
        }

        tournament.setStatus(STATUS_DISBURSED);
        tournamentRepository.save(tournament);

        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Get disbursal status by ID")
    @GetMapping("/{id}/status")
    public ResponseEntity<DisbursalResponse> status(@PathVariable long id) {
        return disbursalRepository.findById(id)
                .map(disbursalMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Optional<ResponseEntity<DisbursalResponse>> rejectUnlessClosed(long tournamentId) {
        Optional<Tournament> maybeTournament = tournamentRepository.findById(tournamentId);
        if (maybeTournament.isEmpty()) {
            return Optional.of(ResponseEntity.notFound().build());
        }
        if (!STATUS_CLOSED.equals(maybeTournament.get().getStatus())) {
            return Optional.of(ResponseEntity.status(HttpStatus.CONFLICT).build());
        }
        return Optional.empty();
    }

    private DisbursalRequest toDisbursalRequest(Tournament tournament, WinnerPayout winner) {
        return new DisbursalRequest(
                UUID.randomUUID().toString(),
                winner.recipientPhone(),
                winner.amountKes(),
                "KES",
                "Prize payout for tournament " + tournament.getId());
    }

    private PrizeDisbursal save(Tournament tournament, WinnerPayout winner, DisbursalOutcome outcome) {
        PrizeDisbursal disbursal = new PrizeDisbursal();
        disbursal.setTournamentId(tournament.getId());
        disbursal.setRecipientPhone(winner.recipientPhone());
        disbursal.setAmountKes(winner.amountKes());
        disbursal.setGateway(outcome.gateway());
        disbursal.setStatus(outcome.result().isSuccessful() ? STATUS_PROCESSING : STATUS_FAILED);
        disbursal.setGatewayRef(outcome.result().gatewayReference());
        return disbursalRepository.save(disbursal);
    }
}
