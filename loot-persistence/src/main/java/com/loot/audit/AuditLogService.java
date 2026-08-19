package com.loot.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loot.domain.model.AuditEvent;
import com.loot.domain.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Records financial events both to the audit_events table (queryable,
 * permanent) and to a dedicated "AUDIT" logger (so they show up in log
 * aggregation alongside everything else once t54's JSON logging lands).
 * A logging or persistence failure here must never fail the payment flow
 * that triggered it - the payment/payout outcome has already been decided
 * by the time any of these are called.
 */
@Service
public class AuditLogService {

    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");
    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditEventRepository repository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void paymentInitiated(long tournamentId, String gateway, BigDecimal amountKes, String reference) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("tournamentId", tournamentId);
        details.put("gateway", gateway);
        details.put("amountKes", amountKes);
        details.put("reference", reference);
        record("PAYMENT_INITIATED", details);
    }

    public void payoutSent(long tournamentId, String gateway, BigDecimal amountKes, String reference, boolean successful) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("tournamentId", tournamentId);
        details.put("gateway", gateway);
        details.put("amountKes", amountKes);
        details.put("reference", reference);
        details.put("successful", successful);
        record("PAYOUT_SENT", details);
    }

    public void gatewayFallback(String fromGateway, String toGateway, String transactionId, String reason) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("fromGateway", fromGateway);
        details.put("toGateway", toGateway);
        details.put("transactionId", transactionId);
        details.put("reason", reason);
        record("GATEWAY_FALLBACK", details);
    }

    private void record(String eventType, Map<String, Object> details) {
        String json = toJson(details);
        try {
            AuditEvent event = new AuditEvent();
            event.setEventType(eventType);
            event.setDetails(json);
            repository.save(event);
        } catch (Exception e) {
            log.error("Failed to persist audit event {}: {}", eventType, e.getMessage());
        }
        auditLog.info("{} {}", eventType, json);
    }

    private String toJson(Map<String, Object> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            return details.toString();
        }
    }
}
