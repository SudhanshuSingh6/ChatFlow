package com.chatflow.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtValidatorTest {

    private static final String SECRET = "test-secret-that-is-at-least-32-characters-long";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private final JwtValidator validator = new JwtValidator(SECRET);

    @Test
    void acceptsAValidToken() {
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(KEY)
                .compact();
        assertThat(validator.isValid(token)).isTrue();
    }

    @Test
    void rejectsGarbage() {
        assertThat(validator.isValid("not-a-jwt")).isFalse();
    }

    @Test
    void rejectsExpiredToken() {
        String expired = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .expiration(new Date(System.currentTimeMillis() - 1000))
                .signWith(KEY)
                .compact();
        assertThat(validator.isValid(expired)).isFalse();
    }

    @Test
    void rejectsTokenSignedWithWrongKey() {
        SecretKey other = Keys.hmacShaKeyFor("another-secret-that-is-also-32-chars-long!!".getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder().subject("x")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(other).compact();
        assertThat(validator.isValid(token)).isFalse();
    }
}
