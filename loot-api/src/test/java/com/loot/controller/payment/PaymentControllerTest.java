package com.loot.controller.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loot.domain.model.EntryPayment;
import com.loot.domain.model.Tournament;
import com.loot.domain.repository.GatewayTransactionRepository;
import com.loot.domain.repository.PaymentRepository;
import com.loot.domain.repository.TournamentRepository;
import com.loot.gateway.CollectionResult;
import com.loot.gateway.orchestration.CollectionOutcome;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(PaymentMapperImpl.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TournamentRepository tournamentRepository;

    @MockitoBean
    private PaymentRepository paymentRepository;

    @MockitoBean
    private GatewayTransactionRepository gatewayTransactionRepository;

    @MockitoBean
    private PaymentOrchestrator paymentOrchestrator;

    @Test
    void collectReturns404WhenTournamentMissing() throws Exception {
        when(tournamentRepository.findById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/payments/collect")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CollectPaymentRequest(1L, "+254712345678"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void collectReturns409WhenTournamentNotOpen() throws Exception {
        Tournament tournament = openTournament(1L, 64);
        tournament.setStatus("CLOSED");
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));

        mockMvc.perform(post("/api/v1/payments/collect")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CollectPaymentRequest(1L, "+254712345678"))))
                .andExpect(status().isConflict());
    }

    @Test
    void collectReturns409WhenTournamentIsFull() throws Exception {
        Tournament tournament = openTournament(1L, 10);
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));
        when(paymentRepository.countByTournamentIdAndStatusNot(1L, "FAILED")).thenReturn(10L);

        mockMvc.perform(post("/api/v1/payments/collect")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CollectPaymentRequest(1L, "+254712345678"))))
                .andExpect(status().isConflict());

        verify(paymentOrchestrator, never()).processCollection(any());
    }

    @Test
    void collectReturns409WhenIdempotencyKeyAlreadyUsed() throws Exception {
        Tournament tournament = openTournament(1L, 64);
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));
        when(paymentRepository.countByTournamentIdAndStatusNot(1L, "FAILED")).thenReturn(0L);
        when(gatewayTransactionRepository.findByIdempotencyKey("dup-key"))
                .thenReturn(Optional.of(new com.loot.domain.model.GatewayTransaction()));

        mockMvc.perform(post("/api/v1/payments/collect")
                        .header("Idempotency-Key", "dup-key")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CollectPaymentRequest(1L, "+254712345678"))))
                .andExpect(status().isConflict());

        verify(paymentOrchestrator, never()).processCollection(any());
    }

    @Test
    void collectReturns201OnSuccess() throws Exception {
        Tournament tournament = openTournament(1L, 64);
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));
        when(paymentRepository.countByTournamentIdAndStatusNot(1L, "FAILED")).thenReturn(0L);
        when(gatewayTransactionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(paymentOrchestrator.processCollection(any()))
                .thenReturn(new CollectionOutcome(new CollectionResult(true, "ws_ref_1", "Accepted"), "MPESA"));
        when(paymentRepository.save(any(EntryPayment.class))).thenAnswer(invocation -> {
            EntryPayment payment = invocation.getArgument(0);
            payment.setId(5L);
            payment.setCreatedAt(Instant.now());
            payment.setUpdatedAt(Instant.now());
            return payment;
        });

        mockMvc.perform(post("/api/v1/payments/collect")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CollectPaymentRequest(1L, "+254712345678"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("INITIATED"))
                .andExpect(jsonPath("$.gateway").value("MPESA"))
                .andExpect(jsonPath("$.reference").value("ws_ref_1"));

        verify(gatewayTransactionRepository).save(any());
    }

    @Test
    void collectReturns402WhenGatewayDeclines() throws Exception {
        Tournament tournament = openTournament(1L, 64);
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));
        when(paymentRepository.countByTournamentIdAndStatusNot(1L, "FAILED")).thenReturn(0L);
        when(gatewayTransactionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(paymentOrchestrator.processCollection(any()))
                .thenReturn(new CollectionOutcome(new CollectionResult(false, null, "Insufficient funds"), "MPESA"));
        when(paymentRepository.save(any(EntryPayment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/v1/payments/collect")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CollectPaymentRequest(1L, "+254712345678"))))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_FAILED"))
                .andExpect(jsonPath("$.message").value("Insufficient funds"));
    }

    @Test
    void collectRejectsInvalidPhoneFormat() throws Exception {
        mockMvc.perform(post("/api/v1/payments/collect")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CollectPaymentRequest(1L, "not-a-phone"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statusReturns404WhenNotFound() throws Exception {
        when(paymentRepository.findByMpesaRef("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/payments/missing/status"))
                .andExpect(status().isNotFound());
    }

    @Test
    void statusReturnsPaymentWhenFound() throws Exception {
        EntryPayment payment = openTournamentPayment();
        when(paymentRepository.findByMpesaRef("ws_ref_1")).thenReturn(Optional.of(payment));

        mockMvc.perform(get("/api/v1/payments/ws_ref_1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value("ws_ref_1"));
    }

    private static Tournament openTournament(long id, int maxEntries) {
        Tournament tournament = new Tournament();
        tournament.setId(id);
        tournament.setName("Friday Cup");
        tournament.setEntryFeeKes(new BigDecimal("100.00"));
        tournament.setMaxEntries(maxEntries);
        tournament.setStatus("OPEN");
        tournament.setCreatedAt(Instant.now());
        tournament.setUpdatedAt(Instant.now());
        return tournament;
    }

    private static EntryPayment openTournamentPayment() {
        EntryPayment payment = new EntryPayment();
        payment.setId(5L);
        payment.setTournamentId(1L);
        payment.setPlayerPhone("+254712345678");
        payment.setAmountKes(new BigDecimal("100.00"));
        payment.setGateway("MPESA");
        payment.setStatus("INITIATED");
        payment.setMpesaRef("ws_ref_1");
        payment.setCreatedAt(Instant.now());
        payment.setUpdatedAt(Instant.now());
        return payment;
    }
}
