package com.loot.gateway.flutterwave;

public record FlutterwaveTransferResponse(
    String status,
    String message,
    Data data
) {

    public record Data(
        Long id,
        String reference,
        String status
    ) {}
}
