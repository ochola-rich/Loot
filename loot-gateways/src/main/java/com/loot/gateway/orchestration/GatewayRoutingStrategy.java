package com.loot.gateway.orchestration;

import com.loot.gateway.CollectionRequest;

public interface GatewayRoutingStrategy {

    /** Returns a gateway identifier such as "MPESA" or "FLUTTERWAVE". */
    String selectGateway(CollectionRequest req);

    /** Returns a fallback gateway identifier, or null if there isn't one. */
    String selectFallback(CollectionRequest req, String primaryGateway);
}
