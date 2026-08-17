package com.loot.exception;

public class PaymentFailedException extends RuntimeException {

    public PaymentFailedException(String gatewayMessage) {
        super(gatewayMessage);
    }
}
