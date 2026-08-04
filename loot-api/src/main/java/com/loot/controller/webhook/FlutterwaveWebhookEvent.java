package com.loot.controller.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FlutterwaveWebhookEvent(String event, Data data) {

    public record Data(
        Long id,
        @JsonProperty("tx_ref") String txRef,
        @JsonProperty("flw_ref") String flwRef,
        String reference,
        String status
    ) {}
}
