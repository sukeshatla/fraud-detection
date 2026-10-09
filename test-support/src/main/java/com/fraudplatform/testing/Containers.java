package com.fraudplatform.testing;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** One place for container images, so every module tests against the same versions as docker-compose. */
public final class Containers {

    public static final String KAFKA_IMAGE = "apache/kafka-native:4.1.0";
    public static final String REDIS_IMAGE = "redis:7.4-alpine";
    public static final String POSTGRES_IMAGE = "postgres:17-alpine";

    private Containers() {}

    public static KafkaContainer kafka() {
        return new KafkaContainer(KAFKA_IMAGE);
    }

    public static GenericContainer<?> redis() {
        return new GenericContainer<>(REDIS_IMAGE).withExposedPorts(6379);
    }

    public static PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(POSTGRES_IMAGE);
    }
}
