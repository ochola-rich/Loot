package com.loot.controller.payment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CollectPaymentRequest(
        @NotNull Long tournamentId,
        @NotNull @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "must be an E.164 phone number") String playerPhone) {
}
