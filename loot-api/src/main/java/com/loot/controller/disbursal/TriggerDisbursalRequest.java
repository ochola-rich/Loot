package com.loot.controller.disbursal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record TriggerDisbursalRequest(
        @NotNull Long tournamentId,
        @NotNull @Valid WinnerPayout winner) {
}
