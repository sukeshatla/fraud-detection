package com.fraudplatform.ingestion.support;

import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

/** Builds a plain {@link StringRedisTemplate} against a Testcontainers Redis, without a Spring context. */
public final class RedisTestSupport {

    public static final String REDIS_IMAGE = "redis:7.4-alpine";

    private RedisTestSupport() {}

    public static GenericContainer<?> redisContainer() {
        return new GenericContainer<>(REDIS_IMAGE).withExposedPorts(6379);
    }

    public static StringRedisTemplate template(GenericContainer<?> redis) {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(redis.getHost(), redis.getMappedPort(6379)));
        factory.afterPropertiesSet();
        factory.start();
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        return template;
    }
}
