package com.fraudplatform.ingestion.support;

import com.fraudplatform.testing.Containers;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Real infrastructure for {@code @SpringBootTest} ITs. Containers are Spring beans, so they start
 * once per cached application context and are shared by every IT that imports this class.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return Containers.kafka();
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return Containers.redis();
    }
}
