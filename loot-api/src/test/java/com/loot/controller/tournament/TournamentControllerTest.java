package com.loot.controller.tournament;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loot.domain.model.Tournament;
import com.loot.domain.repository.TournamentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TournamentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(TournamentMapperImpl.class)
class TournamentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TournamentRepository tournamentRepository;

    @Test
    void createReturns201WithLocationAndOpenStatus() throws Exception {
        when(tournamentRepository.save(any(Tournament.class))).thenAnswer(invocation -> {
            Tournament tournament = invocation.getArgument(0);
            tournament.setId(42L);
            tournament.setCreatedAt(Instant.now());
            tournament.setUpdatedAt(Instant.now());
            return tournament;
        });

        mockMvc.perform(post("/api/v1/tournaments")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CreateTournamentRequest("Friday Cup", new BigDecimal("100.00"), 64))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/tournaments/42")))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.name").value("Friday Cup"));
    }

    @Test
    void createRejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/v1/tournaments")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CreateTournamentRequest("", new BigDecimal("100.00"), 64))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listReturnsPagedTournaments() throws Exception {
        Tournament tournament = openTournament(1L);
        when(tournamentRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(tournament), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/tournaments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getByIdReturns404WhenMissing() throws Exception {
        when(tournamentRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/tournaments/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByIdReturnsTournamentWhenFound() throws Exception {
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(openTournament(1L)));

        mockMvc.perform(get("/api/v1/tournaments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void closeTransitionsOpenTournamentToClosed() throws Exception {
        Tournament tournament = openTournament(1L);
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));
        when(tournamentRepository.save(any(Tournament.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(patch("/api/v1/tournaments/1/close"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        verify(tournamentRepository).save(tournament);
    }

    @Test
    void closeRejectsAlreadyClosedTournament() throws Exception {
        Tournament tournament = openTournament(1L);
        tournament.setStatus("CLOSED");
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));

        mockMvc.perform(patch("/api/v1/tournaments/1/close"))
                .andExpect(status().isConflict());
    }

    @Test
    void closeReturns404WhenMissing() throws Exception {
        when(tournamentRepository.findById(anyLong())).thenReturn(Optional.empty());

        mockMvc.perform(patch("/api/v1/tournaments/1/close"))
                .andExpect(status().isNotFound());
    }

    private static Tournament openTournament(long id) {
        Tournament tournament = new Tournament();
        tournament.setId(id);
        tournament.setName("Friday Cup");
        tournament.setEntryFeeKes(new BigDecimal("100.00"));
        tournament.setMaxEntries(64);
        tournament.setStatus("OPEN");
        tournament.setCreatedAt(Instant.now());
        tournament.setUpdatedAt(Instant.now());
        return tournament;
    }
}
