package com.loot.gateway.flutterwave;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FlutterwaveTransferRequest(
    @JsonProperty("account_bank") String accountBank,
    @JsonProperty("account_number") String accountNumber,
    String amount,
    String currency,
    String narration,
    String reference
) {}
