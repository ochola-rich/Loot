package com.loot.gateway.orchestration;

import com.loot.gateway.CollectionRequest;
import com.loot.gateway.CollectionResult;
import com.loot.gateway.DisbursalRequest;
import com.loot.gateway.DisbursalResult;
import com.loot.gateway.PaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentOrchestratorTest {

    @Mock
    PaymentGateway mpesaGateway;

    @Mock
    PaymentGateway flutterwaveGateway;

    @Mock
    GatewayRoutingStrategy routingStrategy;

    GatewayHealthRegistry healthRegistry;
    ExecutorService executor;
    PaymentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        healthRegistry = new GatewayHealthRegistry();
        executor = Executors.newVirtualThreadPerTaskExecutor();
        Map<String, PaymentGateway> gateways = Map.of(
                "mpesaGateway", mpesaGateway,
                "flutterwaveGateway", flutterwaveGateway
        );
        orchestrator = new PaymentOrchestrator(gateways, routingStrategy, healthRegistry, executor);
    }

    @Test
    void returnsPrimaryResultWithoutTouchingFallbackWhenPrimarySucceeds() {
        CollectionRequest req = collectionRequest();
        when(routingStrategy.selectGateway(req)).thenReturn("MPESA");
        when(mpesaGateway.initiateCollection(req)).thenReturn(new CollectionResult(true, "ref-1", "ok"));

        CollectionOutcome outcome = orchestrator.processCollection(req);

        assertThat(outcome.result().isSuccessful()).isTrue();
        assertThat(outcome.result().gatewayReference()).isEqualTo("ref-1");
        assertThat(outcome.gateway()).isEqualTo("MPESA");
        verify(flutterwaveGateway, never()).initiateCollection(any());
    }

    @Test
    void fallsBackToSecondaryGatewayWhenPrimaryFails() {
        CollectionRequest req = collectionRequest();
        when(routingStrategy.selectGateway(req)).thenReturn("MPESA");
        when(routingStrategy.selectFallback(req, "MPESA")).thenReturn("FLUTTERWAVE");
        when(mpesaGateway.initiateCollection(req)).thenReturn(new CollectionResult(false, null, "declined"));
        when(flutterwaveGateway.initiateCollection(req)).thenReturn(new CollectionResult(true, "ref-2", "ok"));

        CollectionOutcome outcome = orchestrator.processCollection(req);

        assertThat(outcome.result().isSuccessful()).isTrue();
        assertThat(outcome.result().gatewayReference()).isEqualTo("ref-2");
        assertThat(outcome.gateway()).isEqualTo("FLUTTERWAVE");
        verify(mpesaGateway).initiateCollection(req);
        verify(flutterwaveGateway).initiateCollection(req);
    }

    @Test
    void fallsBackWhenPrimaryGatewayThrows() {
        CollectionRequest req = collectionRequest();
        when(routingStrategy.selectGateway(req)).thenReturn("MPESA");
        when(routingStrategy.selectFallback(req, "MPESA")).thenReturn("FLUTTERWAVE");
        when(mpesaGateway.initiateCollection(req)).thenThrow(new RuntimeException("connection refused"));
        when(flutterwaveGateway.initiateCollection(req)).thenReturn(new CollectionResult(true, "ref-3", "ok"));

        CollectionOutcome outcome = orchestrator.processCollection(req);

        assertThat(outcome.result().isSuccessful()).isTrue();
        assertThat(outcome.result().gatewayReference()).isEqualTo("ref-3");
    }

    @Test
    void returnsFailureWithoutFallbackWhenNoFallbackExists() {
        CollectionRequest req = collectionRequest();
        when(routingStrategy.selectGateway(req)).thenReturn("FLUTTERWAVE");
        when(routingStrategy.selectFallback(req, "FLUTTERWAVE")).thenReturn(null);
        when(flutterwaveGateway.initiateCollection(req)).thenReturn(new CollectionResult(false, null, "declined"));

        CollectionOutcome outcome = orchestrator.processCollection(req);

        assertThat(outcome.result().isSuccessful()).isFalse();
        verify(mpesaGateway, never()).initiateCollection(any());
    }

    @Test
    void recordsOutcomesInTheHealthRegistry() {
        CollectionRequest req = collectionRequest();
        when(routingStrategy.selectGateway(req)).thenReturn("MPESA");
        when(mpesaGateway.initiateCollection(req)).thenReturn(new CollectionResult(true, "ref-1", "ok"));

        orchestrator.processCollection(req);

        assertThat(healthRegistry.totalRequests("MPESA")).isEqualTo(1);
        assertThat(healthRegistry.successRate("MPESA")).isEqualTo(1.0);
    }

    @Test
    void bulkPayoutAggregatesResultsIndependentlyIncludingFailures() {
        DisbursalRequest req1 = disbursalRequest("txn-1");
        DisbursalRequest req2 = disbursalRequest("txn-2");
        when(routingStrategy.selectGateway(any())).thenReturn("MPESA");
        when(mpesaGateway.initiatePayout(req1)).thenReturn(new DisbursalResult(true, "ref-1", "ok"));
        when(mpesaGateway.initiatePayout(req2)).thenThrow(new RuntimeException("timeout"));

        List<DisbursalResult> results = orchestrator.processBulkPayout(List.of(req1, req2));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).isSuccessful()).isTrue();
        assertThat(results.get(1).isSuccessful()).isFalse();
    }

    private CollectionRequest collectionRequest() {
        return new CollectionRequest("txn-1", "254712345678", BigDecimal.TEN, "KES", "test");
    }

    private DisbursalRequest disbursalRequest(String transactionId) {
        return new DisbursalRequest(transactionId, "254712345678", BigDecimal.TEN, "KES", "prize");
    }
}
