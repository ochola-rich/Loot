package com.loot.gateway.orchestration;

import com.loot.gateway.CollectionRequest;
import com.loot.gateway.CollectionResult;
import com.loot.gateway.DisbursalRequest;
import com.loot.gateway.DisbursalResult;
import com.loot.gateway.PaymentGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

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

    public PaymentOrchestrator(
            Map<String, PaymentGateway> gatewaysByBeanName,
            GatewayRoutingStrategy routingStrategy,
            GatewayHealthRegistry healthRegistry) {
        this.gatewaysByBeanName = gatewaysByBeanName;
        this.routingStrategy = routingStrategy;
        this.healthRegistry = healthRegistry;
    }

    public CollectionResult processCollection(CollectionRequest req) {
        String primary = routingStrategy.selectGateway(req);
        CollectionResult result = attemptCollection(primary, req);
        if (result.isSuccessful()) {
            return result;
        }

        String fallback = routingStrategy.selectFallback(req, primary);
        if (fallback == null || fallback.equals(primary)) {
            return result;
        }

        // GATEWAY_FALLBACK audit event - structured logging for now; formal
        // AuditLogService persistence lands in t48.
        log.warn("GATEWAY_FALLBACK: {} failed for transaction {} ({}), retrying on {}",
                primary, req.transactionId(), result.responseMessage(), fallback);

        CollectionResult fallbackResult = attemptCollection(fallback, req);
        if (!fallbackResult.isSuccessful()) {
            log.error("Both gateways failed for transaction {}: primary={} ({}), fallback={} ({})",
                    req.transactionId(), primary, result.responseMessage(),
                    fallback, fallbackResult.responseMessage());
        }
        return fallbackResult;
    }

    public DisbursalResult processPayout(DisbursalRequest req) {
        CollectionRequest asCollectionRequest = new CollectionRequest(
                req.transactionId(), req.recipientPhone(), req.amount(), req.currency(), req.description());
        String primary = routingStrategy.selectGateway(asCollectionRequest);
        DisbursalResult result = attemptPayout(primary, req);
        if (result.isSuccessful()) {
            return result;
        }

        String fallback = routingStrategy.selectFallback(asCollectionRequest, primary);
        if (fallback == null || fallback.equals(primary)) {
            return result;
        }

        log.warn("GATEWAY_FALLBACK: {} failed for payout {} ({}), retrying on {}",
                primary, req.transactionId(), result.responseMessage(), fallback);

        DisbursalResult fallbackResult = attemptPayout(fallback, req);
        if (!fallbackResult.isSuccessful()) {
            log.error("Both gateways failed for payout {}: primary={} ({}), fallback={} ({})",
                    req.transactionId(), primary, result.responseMessage(),
                    fallback, fallbackResult.responseMessage());
        }
        return fallbackResult;
    }

    private CollectionResult attemptCollection(String gatewayName, CollectionRequest req) {
        PaymentGateway gateway = resolveGateway(gatewayName);
        if (gateway == null) {
            return new CollectionResult(false, null, "No gateway available for " + gatewayName);
        }
        try {
            CollectionResult result = gateway.initiateCollection(req);
            healthRegistry.record(gatewayName, result.isSuccessful());
            return result;
        } catch (Exception e) {
            healthRegistry.record(gatewayName, false);
            return new CollectionResult(false, null, gatewayName + " threw: " + e.getMessage());
        }
    }

    private DisbursalResult attemptPayout(String gatewayName, DisbursalRequest req) {
        PaymentGateway gateway = resolveGateway(gatewayName);
        if (gateway == null) {
            return new DisbursalResult(false, null, "No gateway available for " + gatewayName);
        }
        try {
            DisbursalResult result = gateway.initiatePayout(req);
            healthRegistry.record(gatewayName, result.isSuccessful());
            return result;
        } catch (Exception e) {
            healthRegistry.record(gatewayName, false);
            return new DisbursalResult(false, null, gatewayName + " threw: " + e.getMessage());
        }
    }

    private PaymentGateway resolveGateway(String gatewayName) {
        return switch (gatewayName) {
            case "MPESA" -> gatewaysByBeanName.get("mpesaGateway");
            case "FLUTTERWAVE" -> gatewaysByBeanName.get("flutterwaveGateway");
            default -> null;
        };
    }
}
