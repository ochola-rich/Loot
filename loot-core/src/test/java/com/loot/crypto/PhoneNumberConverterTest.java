package com.loot.crypto;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNumberConverterTest {

    private static final String KEY = randomBase64Key();

    private final PhoneNumberConverter converter = new PhoneNumberConverter(KEY);

    @Test
    void roundTripsThePlainValue() {
        String ciphertext = converter.convertToDatabaseColumn("+254712345678");

        assertThat(converter.convertToEntityAttribute(ciphertext)).isEqualTo("+254712345678");
    }

    @Test
    void neverStoresThePlainValue() {
        String ciphertext = converter.convertToDatabaseColumn("+254712345678");

        assertThat(ciphertext).doesNotContain("+254712345678");
    }

    @Test
    void producesDifferentCiphertextForTheSameInputEachTime() {
        String first = converter.convertToDatabaseColumn("+254712345678");
        String second = converter.convertToDatabaseColumn("+254712345678");

        assertThat(first).isNotEqualTo(second);
        assertThat(converter.convertToEntityAttribute(first)).isEqualTo("+254712345678");
        assertThat(converter.convertToEntityAttribute(second)).isEqualTo("+254712345678");
    }

    @Test
    void passesNullThrough() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    private static String randomBase64Key() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
