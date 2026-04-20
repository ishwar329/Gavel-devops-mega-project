package com.gavel.bid.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RedisConfigTest {

    @Test
    void stringRedisTemplate_createsTemplate() {
        RedisConfig config = new RedisConfig();
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);

        StringRedisTemplate template = config.stringRedisTemplate(factory);

        assertThat(template).isNotNull();
        assertThat(template.getConnectionFactory()).isEqualTo(factory);
    }
}
