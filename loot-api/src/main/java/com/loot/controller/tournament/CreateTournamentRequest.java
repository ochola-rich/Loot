package com.loot.controller.tournament;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateTournamentRequest(
        @NotBlank String name,
        @NotNull @Positive BigDecimal entryFeeKes,
        @NotNull @Positive Integer maxEntries) {
}
