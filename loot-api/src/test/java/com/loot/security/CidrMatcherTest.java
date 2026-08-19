package com.loot.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CidrMatcherTest {

    @Test
    void matchesAnAddressInsideTheRange() {
        assertThat(CidrMatcher.matches("196.201.214.0/24", "196.201.214.55")).isTrue();
    }

    @Test
    void rejectsAnAddressOutsideTheRange() {
        assertThat(CidrMatcher.matches("196.201.214.0/24", "196.201.215.1")).isFalse();
    }

    @Test
    void matchesASingleHostCidr() {
        assertThat(CidrMatcher.matches("10.0.0.5/32", "10.0.0.5")).isTrue();
        assertThat(CidrMatcher.matches("10.0.0.5/32", "10.0.0.6")).isFalse();
    }

    @Test
    void treatsAMalformedCidrAsNoMatchRatherThanThrowing() {
        assertThat(CidrMatcher.matches("not-a-cidr", "10.0.0.5")).isFalse();
    }
}
