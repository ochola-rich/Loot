package com.loot.controller.payment;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        long id,
        long tournamentId,
        String playerPhone,
        BigDecimal amountKes,
        String gateway,
        String status,
        String reference,
        Instant createdAt,
        Instant updatedAt) {
}
