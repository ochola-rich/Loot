package com.loot.controller.tournament;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateTournamentRequest(
        @NotBlank @Schema(description = "Display name of the tournament", example = "Friday Cup") String name,
        @NotNull @Positive @Schema(description = "Entry fee in KES", example = "100.00") BigDecimal entryFeeKes,
        @NotNull @Positive @Schema(description = "Maximum number of entries accepted", example = "64") Integer maxEntries) {
}
