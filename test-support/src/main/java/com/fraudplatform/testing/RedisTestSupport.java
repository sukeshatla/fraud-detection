package com.fraudplatform.testing;

import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

/** Builds a plain {@link StringRedisTemplate} against a Testcontainers Redis, without a Spring context. */
public final class RedisTestSupport {

    private RedisTestSupport() {}

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
