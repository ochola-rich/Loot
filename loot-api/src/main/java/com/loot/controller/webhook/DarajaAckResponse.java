package com.loot.controller.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DarajaAckResponse(
    @JsonProperty("ResultCode") String resultCode,
    @JsonProperty("ResultDesc") String resultDesc
) {

    public static DarajaAckResponse accepted() {
        return new DarajaAckResponse("0", "Accepted");
    }
}
