package com.loot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.thread.Threading;
import org.springframework.core.env.ConfigurableEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class VirtualThreadsConfigurationTest {

    @Test
    void baseApplicationYamlActivatesVirtualThreads() {
        ConfigurableEnvironment environment = TestEnvironments.loadFor();

        assertThat(Threading.VIRTUAL.isActive(environment)).isTrue();
    }
}
