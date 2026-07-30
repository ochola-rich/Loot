package com.loot.controller.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record StkCallback(
    @JsonProperty("MerchantRequestID") String merchantRequestId,
    @JsonProperty("CheckoutRequestID") String checkoutRequestId,
    @JsonProperty("ResultCode") int resultCode,
    @JsonProperty("ResultDesc") String resultDesc,
    @JsonProperty("CallbackMetadata") CallbackMetadata callbackMetadata
) {

    public record CallbackMetadata(@JsonProperty("Item") List<Item> item) {}

    public record Item(@JsonProperty("Name") String name, @JsonProperty("Value") Object value) {}
}
