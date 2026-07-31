package com.loot.gateway.mpesa;

import com.fasterxml.jackson.annotation.JsonProperty;

public record B2CResponse(
    @JsonProperty("ConversationID") String conversationId,
    @JsonProperty("OriginatorConversationID") String originatorConversationId,
    @JsonProperty("ResponseCode") String responseCode,
    @JsonProperty("ResponseDescription") String responseDescription
) {}
