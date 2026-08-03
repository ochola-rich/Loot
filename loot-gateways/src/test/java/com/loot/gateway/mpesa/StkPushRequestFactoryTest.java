package com.loot.gateway.mpesa;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class StkPushRequestFactoryTest {

    @Test
    void buildsRequestWithComputedPasswordAndTimestamp() {
        StkPushRequestFactory factory = new StkPushRequestFactory("174379", "testpasskey", "https://example.com");
        LocalDateTime fixedNow = LocalDateTime.of(2024, 1, 15, 10, 30, 45);

        StkPushRequest request = factory.build("254712345678", "100", "TXN123", "Entry Fee", fixedNow);

        String expectedPassword = Base64.getEncoder()
                .encodeToString("174379testpasskey20240115103045".getBytes());

        assertThat(request.businessShortCode()).isEqualTo("174379");
        assertThat(request.timestamp()).isEqualTo("20240115103045");
        assertThat(request.password()).isEqualTo(expectedPassword);
        assertThat(request.transactionType()).isEqualTo("CustomerPayBillOnline");
        assertThat(request.amount()).isEqualTo("100");
        assertThat(request.partyA()).isEqualTo("254712345678");
        assertThat(request.partyB()).isEqualTo("174379");
        assertThat(request.phoneNumber()).isEqualTo("254712345678");
        assertThat(request.callBackUrl()).isEqualTo("https://example.com/api/v1/webhooks/mpesa/confirmation");
        assertThat(request.accountReference()).isEqualTo("TXN123");
        assertThat(request.transactionDesc()).isEqualTo("Entry Fee");
    }
}
