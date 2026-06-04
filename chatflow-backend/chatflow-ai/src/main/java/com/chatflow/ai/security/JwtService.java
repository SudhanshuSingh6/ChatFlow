package com.chatflow.ai.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * Verifies caller JWTs minted by core, using the same shared HS256 secret. ai-service only
 * needs to read the subject (the user id) — it never issues tokens. This is the
 * "re-verify in each service" path until the API gateway terminates auth at the edge.
 */
@Slf4j
@Service
public class JwtService {

    private final SecretKey signingKey;

    public JwtService(@Value("${app.jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Optional<UUID> extractUserId(String token) {
        try {
            return Optional.of(UUID.fromString(parseClaims(token).getSubject()));
        } catch (Exception ex) {
            log.debug("Invalid JWT: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
