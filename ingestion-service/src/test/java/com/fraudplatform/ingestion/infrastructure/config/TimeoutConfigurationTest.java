package com.fraudplatform.ingestion.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/** AC-010-06: no outbound call may wait forever. Fails the build if a timeout is removed. */
class TimeoutConfigurationTest {

    private static final Properties CONFIG = load();

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "spring.data.redis.timeout",
            "spring.data.redis.connect-timeout",
            "spring.kafka.producer.properties.request.timeout.ms",
            "spring.kafka.producer.properties.delivery.timeout.ms",
            "spring.lifecycle.timeout-per-shutdown-phase",
            "fraud.ingestion.publish-timeout"
    })
    @DisplayName("AC-010-06: timeout is configured")
    void timeoutIsConfigured(String key) {
        assertThat(CONFIG.getProperty(key)).as(key).isNotBlank();
    }

    @Test
    @DisplayName("AC-010-07: graceful shutdown is on")
    void gracefulShutdown() {
        assertThat(CONFIG.getProperty("server.shutdown")).isEqualTo("graceful");
    }

    private static Properties load() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        return yaml.getObject();
    }
}
