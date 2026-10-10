package com.fraudplatform.alerts.support;

import com.fraudplatform.testing.Containers;
import com.fraudplatform.testing.TestJwts;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** Replaces the JWKS-backed decoder: tests send real tokens signed by {@link TestJwts}. */
    @Bean
    JwtDecoder jwtDecoder() {
        return TestJwts.decoder();
    }

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

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return Containers.postgres();
    }
}
