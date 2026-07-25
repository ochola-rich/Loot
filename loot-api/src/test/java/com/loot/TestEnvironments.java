package com.loot;

import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

final class TestEnvironments {

    private TestEnvironments() {
    }

    static ConfigurableEnvironment loadFor(String... activeProfiles) {
        ConfigurableEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles(activeProfiles);
        ConfigDataEnvironmentPostProcessor.applyTo(environment);
        return environment;
    }

    static String rawProperty(ConfigurableEnvironment environment, String key) {
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (source.containsProperty(key)) {
                Object value = source.getProperty(key);
                if (value != null) {
                    return value.toString();
                }
            }
        }
        return null;
    }
}
