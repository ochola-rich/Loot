package com.loot.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loot.domain.model.AuditEvent;
import com.loot.domain.repository.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditEventRepository repository;

    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogService(repository, new ObjectMapper());
    }

    @Test
    void paymentInitiatedPersistsAnAuditEventWithTheEventDetails() {
        auditLogService.paymentInitiated(1L, "MPESA", new BigDecimal("100.00"), "ws_ref_1");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(captor.capture());

        AuditEvent event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo("PAYMENT_INITIATED");
        assertThat(event.getDetails()).contains("\"tournamentId\":1").contains("\"gateway\":\"MPESA\"").contains("ws_ref_1");
    }

    @Test
    void payoutSentPersistsAnAuditEventWithTheEventDetails() {
        auditLogService.payoutSent(2L, "FLUTTERWAVE", new BigDecimal("500.00"), "flw-ref-9", true);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(captor.capture());

        AuditEvent event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo("PAYOUT_SENT");
        assertThat(event.getDetails()).contains("flw-ref-9").contains("\"successful\":true");
    }

    @Test
    void gatewayFallbackPersistsAnAuditEventWithTheEventDetails() {
        auditLogService.gatewayFallback("MPESA", "FLUTTERWAVE", "txn-1", "Timeout");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(captor.capture());

        AuditEvent event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo("GATEWAY_FALLBACK");
        assertThat(event.getDetails()).contains("\"fromGateway\":\"MPESA\"").contains("\"toGateway\":\"FLUTTERWAVE\"");
    }

    @Test
    void doesNotThrowWhenPersistenceFails() {
        org.mockito.Mockito.doThrow(new RuntimeException("db down")).when(repository).save(org.mockito.ArgumentMatchers.any());

        auditLogService.paymentInitiated(1L, "MPESA", new BigDecimal("100.00"), "ws_ref_1");
    }
}
