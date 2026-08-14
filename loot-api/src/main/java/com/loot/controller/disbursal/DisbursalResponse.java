package com.loot.controller.disbursal;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

public record DisbursalResponse(
        @Schema(description = "Disbursal ID", example = "9") long id,
        @Schema(description = "ID of the tournament this payout is for", example = "1") long tournamentId,
        @Schema(description = "Winner's phone number", example = "+254712345678") String recipientPhone,
        @Schema(description = "Prize amount in KES", example = "5000.00") BigDecimal amountKes,
        @Schema(description = "Gateway that processed the payout", example = "MPESA") String gateway,
        @Schema(description = "Disbursal status", example = "PROCESSING") String status,
        @Schema(description = "Gateway reference for this payout", example = "b2c_ref_1") String reference,
        Instant createdAt,
        Instant updatedAt) {
}
