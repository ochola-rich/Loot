package com.loot;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.ConfigurableEnvironment;

import static com.loot.TestEnvironments.rawProperty;
import static org.assertj.core.api.Assertions.assertThat;

class ApplicationProfilesTest {

    @Test
    void baseApplicationNameIsLoadedRegardlessOfProfile() {
        ConfigurableEnvironment environment = TestEnvironments.loadFor("dev");

        assertThat(environment.getProperty("spring.application.name")).isEqualTo("loot");
    }

    @Test
    void devProfileUsesLocalDatasourceWithSafeDefaults() {
        ConfigurableEnvironment environment = TestEnvironments.loadFor("dev");

        assertThat(rawProperty(environment, "spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://localhost:5432/lootdb");
        assertThat(rawProperty(environment, "daraja.shortcode"))
                .isEqualTo("${DARAJA_SHORTCODE:174379}");
    }

    @Test
    void stagingAndProdProfilesRequireDatasourceAndCredentialsFromEnvironment() {
        for (String profile : new String[] {"staging", "prod"}) {
            ConfigurableEnvironment environment = TestEnvironments.loadFor(profile);

            assertThat(rawProperty(environment, "spring.datasource.url")).isEqualTo("${DATABASE_URL}");
            assertThat(rawProperty(environment, "daraja.consumer-key")).isEqualTo("${DARAJA_CONSUMER_KEY}");
            assertThat(rawProperty(environment, "flutterwave.secret-key")).isEqualTo("${FLW_SECRET_KEY}");
        }
    }
}
