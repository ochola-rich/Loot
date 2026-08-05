package com.loot.domain.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/**
 * Amount + ISO 4217 currency code, normalized to the currency's own decimal
 * precision on construction (UGX has no minor unit in practice; KES/GHS/TZS
 * use 2 decimal places).
 */
public record Money(BigDecimal amount, String currency) {

    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("KES", "UGX", "GHS", "TZS");

    public Money {
        if (currency == null || !SUPPORTED_CURRENCIES.contains(currency)) {
            throw new IllegalArgumentException("Unsupported currency: " + currency);
        }
        if (amount == null) {
            throw new IllegalArgumentException("amount is required");
        }
        amount = amount.setScale(decimalPlaces(currency), RoundingMode.HALF_UP);
    }

    public static int decimalPlaces(String currency) {
        return switch (currency) {
            case "UGX" -> 0;
            case "KES", "GHS", "TZS" -> 2;
            default -> throw new IllegalArgumentException("Unsupported currency: " + currency);
        };
    }
}
