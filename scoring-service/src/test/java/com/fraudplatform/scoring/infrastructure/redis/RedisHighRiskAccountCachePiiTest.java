package com.fraudplatform.scoring.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

/** AC-015-04: account identifiers never reach the logs in clear text. */
@ExtendWith(OutputCaptureExtension.class)
class RedisHighRiskAccountCachePiiTest {

    @Test
    @DisplayName("AC-015-04: a failed eviction logs the masked account id, never the raw one")
    void evictionFailureLogMasksAccount(CapturedOutput output) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        given(redis.delete(anyString())).willThrow(new RedisConnectionFailureException("down"));
        var cache = new RedisHighRiskAccountCache(redis, new SimpleMeterRegistry(), Duration.ofHours(1),
                Duration.ofMinutes(5), 0.1, Duration.ofSeconds(5));

        cache.evict("acc-1001-7788");

        assertThat(output.getAll()).contains("acc-****7788").doesNotContain("acc-1001-7788");
    }
}
