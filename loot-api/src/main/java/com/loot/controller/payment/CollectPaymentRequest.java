package com.loot.controller.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CollectPaymentRequest(
        @NotNull @Schema(description = "ID of the tournament being entered", example = "1") Long tournamentId,
        @NotNull @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "must be an E.164 phone number")
        @Schema(description = "Player's phone number in E.164 format", example = "+254712345678") String playerPhone) {
}
