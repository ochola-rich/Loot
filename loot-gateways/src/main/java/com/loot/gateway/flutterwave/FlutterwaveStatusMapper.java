package com.loot.gateway.flutterwave;

import com.loot.domain.model.PaymentStatus;

public final class FlutterwaveStatusMapper {

    private FlutterwaveStatusMapper() {}

    public static PaymentStatus toPaymentStatus(String flwStatus) {
        if (flwStatus == null) {
            return PaymentStatus.PENDING;
        }
        return switch (flwStatus.toLowerCase()) {
            case "successful" -> PaymentStatus.CONFIRMED;
            case "failed" -> PaymentStatus.FAILED;
            case "pending", "new" -> PaymentStatus.PENDING;
            default -> PaymentStatus.PENDING;
        };
    }
}
