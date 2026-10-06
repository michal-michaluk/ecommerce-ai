package com.example.offer;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Test-only JWT support: a token of the form {@code subject~role} decodes to a JWT carrying the
 * Keycloak {@code realm_access.roles} claim, so the production {@code KeycloakRealmRoles} converter
 * maps it to a {@code ROLE_<role>} authority and the endpoint role guards can be exercised without a
 * running Keycloak.
 */
@TestConfiguration
public class TestSecurityConfiguration {

    @Bean
    JwtDecoder jwtDecoder() {
        return token -> {
            String[] parts = token.split("~", 2);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new BadJwtException("Expected a subject~role token in tests");
            }
            Instant now = Instant.now();
            return Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject(parts[0])
                    .claim("realm_access", Map.of("roles", List.of(parts[1])))
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(3600))
                    .build();
        };
    }
}
