package com.loot.controller.payment;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        @Schema(description = "Payment ID", example = "5") long id,
        @Schema(description = "ID of the tournament this entry fee is for", example = "1") long tournamentId,
        @Schema(description = "Player's phone number", example = "+254712345678") String playerPhone,
        @Schema(description = "Amount charged in KES", example = "100.00") BigDecimal amountKes,
        @Schema(description = "Gateway that processed the collection", example = "MPESA") String gateway,
        @Schema(description = "Payment status", example = "INITIATED") String status,
        @Schema(description = "Gateway reference, usable with GET /{reference}/status", example = "ws_CO_1234")
        String reference,
        Instant createdAt,
        Instant updatedAt) {
}
