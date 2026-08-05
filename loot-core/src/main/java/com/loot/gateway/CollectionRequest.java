package com.loot.gateway;

import java.math.BigDecimal;

public record CollectionRequest(
    String transactionId, // Our internal database ID/reference
    String playerPhone,   // Recipient's phone number
    BigDecimal amount,    // Amount to charge
    String currency,      // ISO 4217 code: KES, UGX, GHS, TZS
    String description    // Description of payment (e.g., "Entry Fee Tournament)
) {}
