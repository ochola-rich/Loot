package com.loot.gateway.mpesa;

import com.fasterxml.jackson.annotation.JsonProperty;

public record B2CRequest(
    @JsonProperty("InitiatorName") String initiatorName,
    @JsonProperty("SecurityCredential") String securityCredential,
    @JsonProperty("CommandID") String commandId,
    @JsonProperty("Amount") String amount,
    @JsonProperty("PartyA") String partyA,
    @JsonProperty("PartyB") String partyB,
    @JsonProperty("Remarks") String remarks,
    @JsonProperty("QueueTimeOutURL") String queueTimeOutUrl,
    @JsonProperty("ResultURL") String resultUrl,
    @JsonProperty("Occasion") String occasion
) {}
