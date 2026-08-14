package com.loot.controller.tournament;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

public record TournamentResponse(
        @Schema(description = "Tournament ID", example = "1") long id,
        @Schema(description = "Display name of the tournament", example = "Friday Cup") String name,
        @Schema(description = "Entry fee in KES", example = "100.00") BigDecimal entryFeeKes,
        @Schema(description = "Maximum number of entries accepted", example = "64") Integer maxEntries,
        @Schema(description = "Tournament status", example = "OPEN") String status,
        Instant createdAt,
        Instant updatedAt) {
}
