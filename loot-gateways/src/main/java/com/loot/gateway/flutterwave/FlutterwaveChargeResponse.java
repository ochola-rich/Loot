package com.loot.gateway.flutterwave;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FlutterwaveChargeResponse(
    String status,
    String message,
    Data data
) {

    public record Data(
        Long id,
        @JsonProperty("tx_ref") String txRef,
        @JsonProperty("flw_ref") String flwRef,
        String status
    ) {}
}
