package com.loot.controller.tournament;

import com.loot.domain.model.Tournament;
import com.loot.domain.repository.TournamentRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/tournaments")
public class TournamentController {

    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_CLOSED = "CLOSED";

    private final TournamentRepository tournamentRepository;
    private final TournamentMapper tournamentMapper;

    public TournamentController(TournamentRepository tournamentRepository, TournamentMapper tournamentMapper) {
        this.tournamentRepository = tournamentRepository;
        this.tournamentMapper = tournamentMapper;
    }

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

    @GetMapping
    public Page<TournamentResponse> list(Pageable pageable) {
        return tournamentRepository.findAll(pageable).map(tournamentMapper::toResponse);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TournamentResponse> getById(@PathVariable long id) {
        return tournamentRepository.findById(id)
                .map(tournamentMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/close")
    public ResponseEntity<TournamentResponse> close(@PathVariable long id) {
        return tournamentRepository.findById(id)
                .map(tournament -> {
                    if (!STATUS_OPEN.equals(tournament.getStatus())) {
                        return ResponseEntity.status(409).<TournamentResponse>build();
                    }
                    tournament.setStatus(STATUS_CLOSED);
                    Tournament saved = tournamentRepository.save(tournament);
                    return ResponseEntity.ok(tournamentMapper.toResponse(saved));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
