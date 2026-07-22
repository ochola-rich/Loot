package com.loot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.thread.Threading;
import org.springframework.mock.env.MockEnvironment;

import java.io.InputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class VirtualThreadsConfigurationTest {

    @Test
    void applicationPropertiesActivateVirtualThreads() throws Exception {
        Properties props = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            props.load(in);
        }

        MockEnvironment environment = new MockEnvironment();
        props.forEach((key, value) -> environment.withProperty((String) key, (String) value));

        assertThat(Threading.VIRTUAL.isActive(environment)).isTrue();
    }
}
