package com.loot.controller.disbursal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loot.audit.AuditLogService;
import com.loot.domain.model.PrizeDisbursal;
import com.loot.domain.model.Tournament;
import com.loot.domain.repository.DisbursalRepository;
import com.loot.domain.repository.TournamentRepository;
import com.loot.gateway.DisbursalResult;
import com.loot.gateway.orchestration.DisbursalOutcome;
import com.loot.gateway.orchestration.PaymentOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DisbursalController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(DisbursalMapperImpl.class)
class DisbursalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TournamentRepository tournamentRepository;

    @MockitoBean
    private DisbursalRepository disbursalRepository;

    @MockitoBean
    private PaymentOrchestrator paymentOrchestrator;

    @MockitoBean
    private AuditLogService auditLogService;

    @Test
    void triggerReturns404WhenTournamentMissing() throws Exception {
        when(tournamentRepository.findById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/disbursals/trigger")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new TriggerDisbursalRequest(1L, new WinnerPayout("+254712345678", BigDecimal.TEN)))))
                .andExpect(status().isNotFound());
    }

    @Test
    void triggerReturns409WhenTournamentNotClosed() throws Exception {
        Tournament tournament = closedTournament(1L);
        tournament.setStatus("OPEN");
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));

        mockMvc.perform(post("/api/v1/disbursals/trigger")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new TriggerDisbursalRequest(1L, new WinnerPayout("+254712345678", BigDecimal.TEN)))))
                .andExpect(status().isConflict());

        verify(paymentOrchestrator, never()).processPayout(any());
    }

    @Test
    void triggerReturns201OnSuccess() throws Exception {
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(closedTournament(1L)));
        when(paymentOrchestrator.processPayout(any()))
                .thenReturn(new DisbursalOutcome(new DisbursalResult(true, "b2c_ref_1", "Accepted"), "MPESA"));
        when(disbursalRepository.save(any(PrizeDisbursal.class))).thenAnswer(invocation -> {
            PrizeDisbursal disbursal = invocation.getArgument(0);
            disbursal.setId(9L);
            disbursal.setCreatedAt(Instant.now());
            disbursal.setUpdatedAt(Instant.now());
            return disbursal;
        });

        mockMvc.perform(post("/api/v1/disbursals/trigger")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new TriggerDisbursalRequest(1L, new WinnerPayout("+254712345678", BigDecimal.TEN)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.reference").value("b2c_ref_1"));

        verify(tournamentRepository, never()).save(any());
    }

    @Test
    void bulkMarksTournamentDisbursedAfterCompletion() throws Exception {
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(closedTournament(1L)));
        when(paymentOrchestrator.processBulkPayout(anyList())).thenReturn(List.of(
                new DisbursalOutcome(new DisbursalResult(true, "ref-1", "ok"), "MPESA"),
                new DisbursalOutcome(new DisbursalResult(false, null, "declined"), "MPESA")
        ));
        when(disbursalRepository.save(any(PrizeDisbursal.class))).thenAnswer(invocation -> {
            PrizeDisbursal disbursal = invocation.getArgument(0);
            disbursal.setId(1L);
            disbursal.setCreatedAt(Instant.now());
            disbursal.setUpdatedAt(Instant.now());
            return disbursal;
        });

        mockMvc.perform(post("/api/v1/disbursals/bulk")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new BulkDisbursalRequest(1L, List.of(
                                new WinnerPayout("+254712345678", BigDecimal.TEN),
                                new WinnerPayout("+254798765432", BigDecimal.ONE))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PROCESSING"))
                .andExpect(jsonPath("$[1].status").value("FAILED"));

        verify(tournamentRepository).save(argThat(t -> "DISBURSED".equals(t.getStatus())));
    }

    @Test
    void bulkRejectsEmptyWinnerList() throws Exception {
        mockMvc.perform(post("/api/v1/disbursals/bulk")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new BulkDisbursalRequest(1L, List.of()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statusReturns404WhenMissing() throws Exception {
        when(disbursalRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/disbursals/99/status"))
                .andExpect(status().isNotFound());
    }

    @Test
    void statusReturnsDisbursalWhenFound() throws Exception {
        PrizeDisbursal disbursal = new PrizeDisbursal();
        disbursal.setId(1L);
        disbursal.setTournamentId(1L);
        disbursal.setRecipientPhone("+254712345678");
        disbursal.setAmountKes(BigDecimal.TEN);
        disbursal.setGateway("MPESA");
        disbursal.setStatus("PROCESSING");
        disbursal.setGatewayRef("ref-1");
        disbursal.setCreatedAt(Instant.now());
        disbursal.setUpdatedAt(Instant.now());
        when(disbursalRepository.findById(1L)).thenReturn(Optional.of(disbursal));

        mockMvc.perform(get("/api/v1/disbursals/1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value("ref-1"));
    }

    private static Tournament closedTournament(long id) {
        Tournament tournament = new Tournament();
        tournament.setId(id);
        tournament.setName("Friday Cup");
        tournament.setEntryFeeKes(new BigDecimal("100.00"));
        tournament.setMaxEntries(64);
        tournament.setStatus("CLOSED");
        tournament.setCreatedAt(Instant.now());
        tournament.setUpdatedAt(Instant.now());
        return tournament;
    }
}
