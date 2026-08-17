package com.loot.controller.disbursal;

import com.loot.domain.model.PrizeDisbursal;
import com.loot.domain.model.Tournament;
import com.loot.domain.repository.DisbursalRepository;
import com.loot.domain.repository.TournamentRepository;
import com.loot.exception.PaymentFailedException;
import com.loot.exception.TournamentNotFoundException;
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
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
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
        Tournament tournament = findClosedOrThrow(request.tournamentId());

        DisbursalRequest payoutRequest = toDisbursalRequest(tournament, request.winner());
        DisbursalOutcome outcome = paymentOrchestrator.processPayout(payoutRequest);
        PrizeDisbursal saved = save(tournament, request.winner(), outcome);

        if (!outcome.result().isSuccessful()) {
            throw new PaymentFailedException(outcome.result().responseMessage());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(disbursalMapper.toResponse(saved));
    }

    @Operation(summary = "Bulk prize payout",
            description = "Pays a list of winners concurrently and marks the tournament DISBURSED once the run "
                    + "completes, regardless of individual per-winner outcome. Rejects with 409 unless the "
                    + "tournament is CLOSED.")
    @PostMapping("/bulk")
    public ResponseEntity<List<DisbursalResponse>> bulk(@Valid @RequestBody BulkDisbursalRequest request) {
        Tournament tournament = findClosedOrThrow(request.tournamentId());

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
    public DisbursalResponse status(@PathVariable long id) {
        return disbursalRepository.findById(id)
                .map(disbursalMapper::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Disbursal " + id + " not found"));
    }

    private Tournament findClosedOrThrow(long tournamentId) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new TournamentNotFoundException(tournamentId));
        if (!STATUS_CLOSED.equals(tournament.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tournament " + tournamentId + " is not CLOSED");
        }
        return tournament;
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
