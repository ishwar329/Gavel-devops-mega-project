package com.gavel.shared.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider("this-is-a-test-secret-key-that-is-long-enough-for-hmac");
    }

    @Test
    void generateToken_containsCorrectClaims() {
        String token = provider.generateToken("user-1", "alice", "alice@test.com", "buyer");

        Claims claims = provider.parseToken(token);
        assertEquals("user-1", claims.getSubject());
        assertEquals("alice", claims.get("username", String.class));
        assertEquals("alice@test.com", claims.get("email", String.class));
        assertEquals("buyer", claims.get("role", String.class));
    }

    @Test
    void generateToken_setsExpiration() {
        String token = provider.generateToken("user-1", "alice", "alice@test.com", "buyer");

        Claims claims = provider.parseToken(token);
        assertNotNull(claims.getExpiration());
        assertNotNull(claims.getIssuedAt());
        assertTrue(claims.getExpiration().after(claims.getIssuedAt()));
    }

    @Test
    void isValid_validToken_returnsTrue() {
        String token = provider.generateToken("user-1", "alice", "alice@test.com", "buyer");
        assertTrue(provider.isValid(token));
    }

    @Test
    void isValid_invalidToken_returnsFalse() {
        assertFalse(provider.isValid("not-a-real-token"));
    }

    @Test
    void isValid_tamperedToken_returnsFalse() {
        String token = provider.generateToken("user-1", "alice", "alice@test.com", "buyer");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertFalse(provider.isValid(tampered));
    }

    @Test
    void isValid_emptyToken_returnsFalse() {
        assertFalse(provider.isValid(""));
    }

    @Test
    void parseToken_differentSecret_throwsException() {
        String token = provider.generateToken("user-1", "alice", "alice@test.com", "buyer");
        JwtTokenProvider other = new JwtTokenProvider("a-completely-different-secret-key-that-is-long-enough");
        assertThrows(Exception.class, () -> other.parseToken(token));
    }

    @Test
    void generateToken_sellerRole() {
        String token = provider.generateToken("s-1", "bob", "bob@shop.com", "seller");
        Claims claims = provider.parseToken(token);
        assertEquals("seller", claims.get("role", String.class));
    }
}
