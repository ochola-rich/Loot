package com.loot.controller.tournament;

import com.loot.domain.model.Tournament;
import com.loot.domain.repository.TournamentRepository;
import com.loot.exception.TournamentNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/tournaments")
@Tag(name = "Tournaments")
public class TournamentController {

    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_CLOSED = "CLOSED";

    private final TournamentRepository tournamentRepository;
    private final TournamentMapper tournamentMapper;

    public TournamentController(TournamentRepository tournamentRepository, TournamentMapper tournamentMapper) {
        this.tournamentRepository = tournamentRepository;
        this.tournamentMapper = tournamentMapper;
    }

    @Operation(summary = "Create tournament", description = "Creates a new tournament in OPEN status.")
    @PostMapping
    public ResponseEntity<TournamentResponse> create(
            @Valid @RequestBody CreateTournamentRequest request, UriComponentsBuilder uriBuilder) {
        Tournament tournament = tournamentMapper.toEntity(request);
        tournament.setStatus(STATUS_OPEN);
        Tournament saved = tournamentRepository.save(tournament);

        return ResponseEntity
                .created(uriBuilder.path("/api/v1/tournaments/{id}").build(saved.getId()))
                .body(tournamentMapper.toResponse(saved));
    }

    @Operation(summary = "List tournaments", description = "Paginated list of all tournaments.")
    @GetMapping
    public Page<TournamentResponse> list(Pageable pageable) {
        return tournamentRepository.findAll(pageable).map(tournamentMapper::toResponse);
    }

    @Operation(summary = "Get tournament by ID")
    @GetMapping("/{id}")
    public TournamentResponse getById(@PathVariable long id) {
        return tournamentMapper.toResponse(findOrThrow(id));
    }

    @Operation(summary = "Close tournament",
            description = "Transitions an OPEN tournament to CLOSED. Rejects a tournament that isn't OPEN with 409.")
    @PatchMapping("/{id}/close")
    public TournamentResponse close(@PathVariable long id) {
        Tournament tournament = findOrThrow(id);
        if (!STATUS_OPEN.equals(tournament.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tournament " + id + " is not OPEN");
        }
        tournament.setStatus(STATUS_CLOSED);
        return tournamentMapper.toResponse(tournamentRepository.save(tournament));
    }

    private Tournament findOrThrow(long id) {
        return tournamentRepository.findById(id).orElseThrow(() -> new TournamentNotFoundException(id));
    }
}
