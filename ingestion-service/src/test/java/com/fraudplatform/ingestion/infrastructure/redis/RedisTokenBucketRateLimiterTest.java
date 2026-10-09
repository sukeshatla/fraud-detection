package com.fraudplatform.ingestion.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

import com.fraudplatform.ingestion.application.RateLimitDecision;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RedisTokenBucketRateLimiterTest {

    @Mock
    private StringRedisTemplate redis;

    @Test
    @DisplayName("AC-002-04: Redis unavailable → fail open (allowed) and count the failure")
    void failsOpenWhenRedisIsDown() {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        given(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .willThrow(new RedisConnectionFailureException("connection refused"));
        RedisTokenBucketRateLimiter limiter =
                new RedisTokenBucketRateLimiter(redis, client -> new Quota(100, 50), meters);

        RateLimitDecision decision = limiter.tryAcquire("gw-1");

        assertThat(decision.allowed()).isTrue();
        assertThat(meters.get("rate_limiter_failures_total").counter().count()).isEqualTo(1.0);
    }
}
