package com.loot.gateway.orchestration;

import com.loot.audit.AuditLogService;
import com.loot.gateway.CollectionRequest;
import com.loot.gateway.CollectionResult;
import com.loot.gateway.DisbursalRequest;
import com.loot.gateway.DisbursalResult;
import com.loot.gateway.PaymentGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Selects a gateway via GatewayRoutingStrategy, calls it, and on failure
 * retries once on the fallback gateway (same CollectionRequest/
 * DisbursalRequest instance both times, so the transactionId - and once
 * IdempotencyService lands in t50, the idempotency key - stays consistent
 * across the retry). Caps at 2 total gateway attempts per the plan.
 *
 * This class stays thin on purpose: it orchestrates, it doesn't implement
 * gateway-specific logic - that lives in MpesaGateway/FlutterwaveGateway.
 */
@Service
public class PaymentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PaymentOrchestrator.class);

    private final Map<String, PaymentGateway> gatewaysByBeanName;
    private final GatewayRoutingStrategy routingStrategy;
    private final GatewayHealthRegistry healthRegistry;
    private final ExecutorService payoutExecutor;
    private final AuditLogService auditLogService;

    public PaymentOrchestrator(
            Map<String, PaymentGateway> gatewaysByBeanName,
            GatewayRoutingStrategy routingStrategy,
            GatewayHealthRegistry healthRegistry,
            ExecutorService payoutExecutor,
            AuditLogService auditLogService) {
        this.gatewaysByBeanName = gatewaysByBeanName;
        this.routingStrategy = routingStrategy;
        this.healthRegistry = healthRegistry;
        this.payoutExecutor = payoutExecutor;
        this.auditLogService = auditLogService;
    }

    /**
     * Dispatches each payout to its own virtual thread and waits for all of
     * them - a blocking I/O call per winner is exactly what virtual threads
     * are for, no thread pool sizing needed even for a large winner list.
     * Partial failure is expected: one winner's payout failing doesn't stop
     * or affect the others, it's just recorded in that slot of the result.
     */
    public List<DisbursalOutcome> processBulkPayout(List<DisbursalRequest> payouts) {
        List<Future<DisbursalOutcome>> futures = new ArrayList<>(payouts.size());
        for (DisbursalRequest req : payouts) {
            futures.add(payoutExecutor.submit(() -> processPayout(req)));
        }

        List<DisbursalOutcome> results = new ArrayList<>(payouts.size());
        for (int i = 0; i < futures.size(); i++) {
            DisbursalRequest req = payouts.get(i);
            try {
                results.add(futures.get(i).get());
            } catch (Exception e) {
                log.error("Payout task failed for {}: {}", req.transactionId(), e.getMessage());
                results.add(new DisbursalOutcome(
                        new DisbursalResult(false, null, "Payout task failed: " + e.getMessage()), null));
            }
        }
        return results;
    }

    public CollectionOutcome processCollection(CollectionRequest req) {
        String primary = routingStrategy.selectGateway(req);
        CollectionResult result = attemptCollection(primary, req);
        if (result.isSuccessful()) {
            return new CollectionOutcome(result, primary);
        }

        String fallback = routingStrategy.selectFallback(req, primary);
        if (fallback == null || fallback.equals(primary)) {
            return new CollectionOutcome(result, primary);
        }

        auditLogService.gatewayFallback(primary, fallback, req.transactionId(), result.responseMessage());

        CollectionResult fallbackResult = attemptCollection(fallback, req);
        if (!fallbackResult.isSuccessful()) {
            log.error("Both gateways failed for transaction {}: primary={} ({}), fallback={} ({})",
                    req.transactionId(), primary, result.responseMessage(),
                    fallback, fallbackResult.responseMessage());
        }
        return new CollectionOutcome(fallbackResult, fallback);
    }

    public DisbursalOutcome processPayout(DisbursalRequest req) {
        CollectionRequest asCollectionRequest = new CollectionRequest(
                req.transactionId(), req.recipientPhone(), req.amount(), req.currency(), req.description());
        String primary = routingStrategy.selectGateway(asCollectionRequest);
        DisbursalResult result = attemptPayout(primary, req);
        if (result.isSuccessful()) {
            return new DisbursalOutcome(result, primary);
        }

        String fallback = routingStrategy.selectFallback(asCollectionRequest, primary);
        if (fallback == null || fallback.equals(primary)) {
            return new DisbursalOutcome(result, primary);
        }

        auditLogService.gatewayFallback(primary, fallback, req.transactionId(), result.responseMessage());

        DisbursalResult fallbackResult = attemptPayout(fallback, req);
        if (!fallbackResult.isSuccessful()) {
            log.error("Both gateways failed for payout {}: primary={} ({}), fallback={} ({})",
                    req.transactionId(), primary, result.responseMessage(),
                    fallback, fallbackResult.responseMessage());
        }
        return new DisbursalOutcome(fallbackResult, fallback);
    }

    private CollectionResult attemptCollection(String gatewayName, CollectionRequest req) {
        PaymentGateway gateway = resolveGateway(gatewayName);
        if (gateway == null) {
            return new CollectionResult(false, null, "No gateway available for " + gatewayName);
        }
        withGatewayMdc(req.transactionId(), gatewayName, req.amount());
        try {
            CollectionResult result = gateway.initiateCollection(req);
            healthRegistry.record(gatewayName, result.isSuccessful());
            return result;
        } catch (Exception e) {
            healthRegistry.record(gatewayName, false);
            return new CollectionResult(false, null, gatewayName + " threw: " + e.getMessage());
        } finally {
            clearGatewayMdc();
        }
    }

    private DisbursalResult attemptPayout(String gatewayName, DisbursalRequest req) {
        PaymentGateway gateway = resolveGateway(gatewayName);
        if (gateway == null) {
            return new DisbursalResult(false, null, "No gateway available for " + gatewayName);
        }
        withGatewayMdc(req.transactionId(), gatewayName, req.amount());
        try {
            DisbursalResult result = gateway.initiatePayout(req);
            healthRegistry.record(gatewayName, result.isSuccessful());
            return result;
        } catch (Exception e) {
            healthRegistry.record(gatewayName, false);
            return new DisbursalResult(false, null, gatewayName + " threw: " + e.getMessage());
        } finally {
            clearGatewayMdc();
        }
    }

    private void withGatewayMdc(String transactionId, String gatewayName, BigDecimal amount) {
        MDC.put("transactionId", transactionId);
        MDC.put("gateway", gatewayName);
        MDC.put("amount", amount.toPlainString());
    }

    private void clearGatewayMdc() {
        MDC.remove("transactionId");
        MDC.remove("gateway");
        MDC.remove("amount");
    }

    private PaymentGateway resolveGateway(String gatewayName) {
        return switch (gatewayName) {
            case "MPESA" -> gatewaysByBeanName.get("mpesaGateway");
            case "FLUTTERWAVE" -> gatewaysByBeanName.get("flutterwaveGateway");
            default -> null;
        };
    }
}
