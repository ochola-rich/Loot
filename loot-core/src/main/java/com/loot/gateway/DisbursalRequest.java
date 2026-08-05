package com.loot.gateway;

import java.math.BigDecimal;

public record DisbursalRequest(
    String transactionId,
    String recipientPhone,
    BigDecimal amount,
    String currency, // ISO 4217 code: KES, UGX, GHS, TZS
    String description
) {}
