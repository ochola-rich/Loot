package com.loot.domain.money;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CurrencyGatewaySupportTest {

    @Test
    void mpesaOnlySupportsKes() {
        assertThat(CurrencyGatewaySupport.isSupported("KES", "MPESA")).isTrue();
        assertThat(CurrencyGatewaySupport.isSupported("UGX", "MPESA")).isFalse();
        assertThat(CurrencyGatewaySupport.isSupported("GHS", "MPESA")).isFalse();
    }

    @Test
    void flutterwaveSupportsAllFourCurrencies() {
        assertThat(CurrencyGatewaySupport.isSupported("KES", "FLUTTERWAVE")).isTrue();
        assertThat(CurrencyGatewaySupport.isSupported("UGX", "FLUTTERWAVE")).isTrue();
        assertThat(CurrencyGatewaySupport.isSupported("GHS", "FLUTTERWAVE")).isTrue();
        assertThat(CurrencyGatewaySupport.isSupported("TZS", "FLUTTERWAVE")).isTrue();
    }

    @Test
    void unknownCurrencyIsUnsupportedByAnyGateway() {
        assertThat(CurrencyGatewaySupport.isSupported("USD", "FLUTTERWAVE")).isFalse();
    }
}
