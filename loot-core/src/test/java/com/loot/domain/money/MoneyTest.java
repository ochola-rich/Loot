package com.loot.domain.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void roundsKesToTwoDecimalPlaces() {
        Money money = new Money(new BigDecimal("100.5"), "KES");

        assertThat(money.amount()).isEqualByComparingTo("100.50");
    }

    @Test
    void roundsUgxToZeroDecimalPlaces() {
        Money money = new Money(new BigDecimal("1500.75"), "UGX");

        assertThat(money.amount()).isEqualByComparingTo("1501");
    }

    @Test
    void rejectsUnsupportedCurrency() {
        assertThatThrownBy(() -> new Money(BigDecimal.TEN, "USD"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullAmount() {
        assertThatThrownBy(() -> new Money(null, "KES"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
