package com.loot.controller.disbursal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record BulkDisbursalRequest(
        @NotNull @Schema(description = "ID of the CLOSED tournament to disburse", example = "1") Long tournamentId,
        @NotEmpty @Valid List<WinnerPayout> winners) {
}
