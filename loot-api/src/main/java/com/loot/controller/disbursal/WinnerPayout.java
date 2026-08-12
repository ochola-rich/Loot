package com.loot.controller.disbursal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record WinnerPayout(
        @NotBlank @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "must be an E.164 phone number") String recipientPhone,
        @NotNull @Positive BigDecimal amountKes) {
}
