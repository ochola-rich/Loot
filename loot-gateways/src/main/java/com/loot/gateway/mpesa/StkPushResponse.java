package com.loot.gateway.mpesa;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StkPushResponse(
    @JsonProperty("MerchantRequestID") String merchantRequestId,
    @JsonProperty("CheckoutRequestID") String checkoutRequestId,
    @JsonProperty("ResponseCode") String responseCode,
    @JsonProperty("ResponseDescription") String responseDescription,
    @JsonProperty("CustomerMessage") String customerMessage
) {}
