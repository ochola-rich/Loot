package com.loot.controller.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record B2CResultEnvelope(@JsonProperty("Result") Result result) {

    public record Result(
        @JsonProperty("ResultType") int resultType,
        @JsonProperty("ResultCode") int resultCode,
        @JsonProperty("ResultDesc") String resultDesc,
        @JsonProperty("OriginatorConversationID") String originatorConversationId,
        @JsonProperty("ConversationID") String conversationId,
        @JsonProperty("TransactionID") String transactionId,
        @JsonProperty("ResultParameters") ResultParameters resultParameters
    ) {}

    public record ResultParameters(@JsonProperty("ResultParameter") List<ResultParameter> resultParameter) {}

    public record ResultParameter(@JsonProperty("Key") String key, @JsonProperty("Value") Object value) {}
}
