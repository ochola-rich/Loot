package com.loot.gateway.flutterwave;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FlutterwaveChargeRequest(
    @JsonProperty("tx_ref") String txRef,
    String amount,
    String currency,
    String email,
    @JsonProperty("phone_number") String phoneNumber,
    String fullname
) {}
