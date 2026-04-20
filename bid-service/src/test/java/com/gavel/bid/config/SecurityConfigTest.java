package com.gavel.bid.config;

import com.gavel.shared.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    @Test
    void jwtTokenProvider_createsProvider() {
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "jwtSecret", "test-secret-that-is-long-enough-for-jwt-hmac-sha-256-algorithm");

        JwtTokenProvider provider = config.jwtTokenProvider();

        assertThat(provider).isNotNull();
    }

}
