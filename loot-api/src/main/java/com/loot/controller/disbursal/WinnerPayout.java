package com.loot.controller.disbursal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record WinnerPayout(
        @NotBlank @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "must be an E.164 phone number")
        @Schema(description = "Winner's phone number in E.164 format", example = "+254712345678") String recipientPhone,
        @NotNull @Positive @Schema(description = "Prize amount in KES", example = "5000.00") BigDecimal amountKes) {
}
