package com.chatflow.realtime.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/** Verifies handshake JWTs minted by core using the shared HS256 secret. Read-only (no issuing). */
@Slf4j
@Service
public class JwtService {

    private final SecretKey signingKey;

    public JwtService(@Value("${app.jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Optional<UUID> extractUserId(String token) {
        try {
            return Optional.of(UUID.fromString(
                    Jwts.parser().verifyWith(signingKey).build()
                            .parseSignedClaims(token).getPayload().getSubject()));
        } catch (Exception ex) {
            log.debug("Invalid WS token: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
