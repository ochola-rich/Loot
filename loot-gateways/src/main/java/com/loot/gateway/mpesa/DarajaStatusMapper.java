package com.loot.gateway.mpesa;

import com.loot.domain.model.PaymentStatus;

public final class DarajaStatusMapper {

    private DarajaStatusMapper() {}

    public static PaymentStatus toPaymentStatus(int resultCode) {
        return resultCode == 0 ? PaymentStatus.CONFIRMED : PaymentStatus.FAILED;
    }
}
