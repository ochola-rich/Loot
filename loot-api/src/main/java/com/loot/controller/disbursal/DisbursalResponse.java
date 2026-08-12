package com.loot.controller.disbursal;

import java.math.BigDecimal;
import java.time.Instant;

public record DisbursalResponse(
        long id,
        long tournamentId,
        String recipientPhone,
        BigDecimal amountKes,
        String gateway,
        String status,
        String reference,
        Instant createdAt,
        Instant updatedAt) {
}
