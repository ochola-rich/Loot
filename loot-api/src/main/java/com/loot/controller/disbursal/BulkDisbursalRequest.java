package com.loot.controller.disbursal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record BulkDisbursalRequest(
        @NotNull Long tournamentId,
        @NotEmpty @Valid List<WinnerPayout> winners) {
}
