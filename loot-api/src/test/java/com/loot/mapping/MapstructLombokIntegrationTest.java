package com.loot.mapping;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MapstructLombokIntegrationTest {

    @Test
    void mapstructProcessorReadsLombokGeneratedAccessors() {
        SampleSource source = SampleSource.builder().name("entry-fee").amount(500).build();

        SampleDestination destination = SampleMapper.INSTANCE.toDestination(source);

        assertThat(destination).isEqualTo(new SampleDestination("entry-fee", 500));
    }
}
