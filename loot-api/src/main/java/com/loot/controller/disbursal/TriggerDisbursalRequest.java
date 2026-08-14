package com.loot.controller.disbursal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record TriggerDisbursalRequest(
        @NotNull @Schema(description = "ID of the CLOSED tournament to pay a winner from", example = "1") Long tournamentId,
        @NotNull @Valid WinnerPayout winner) {
}
