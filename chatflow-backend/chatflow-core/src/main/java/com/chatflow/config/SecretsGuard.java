package com.chatflow.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fails the app fast in production if security-sensitive values are still their dev defaults —
 * a cheap guard against accidentally shipping the placeholder JWT secret / internal token.
 * Active only under the {@code prod} profile.
 */
@Component
@Profile("prod")
@RequiredArgsConstructor
public class SecretsGuard implements InitializingBean {

    private static final String DEFAULT_JWT_SECRET = "change-me-in-production-must-be-at-least-32-chars";
    private static final String DEFAULT_INTERNAL_TOKEN = "dev-internal-token";

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.internal.token:dev-internal-token}")
    private String internalToken;

    @Override
    public void afterPropertiesSet() {
        if (DEFAULT_JWT_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException("Refusing to start in prod: app.jwt.secret is still the dev default");
        }
        if (DEFAULT_INTERNAL_TOKEN.equals(internalToken)) {
            throw new IllegalStateException("Refusing to start in prod: app.internal.token is still the dev default");
        }
    }
}
