package com.example.offer.auth;

import com.example.offer.tools.JsonConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the Keycloak test realm exposes the two element-02 roles: the tokens minted for
 * the seeded {@code carla} / {@code sara} users carry {@code content-manager} / {@code sales}
 * in {@code realm_access.roles}, which {@link com.example.offer.security.KeycloakRealmRoles}
 * then turns into {@code ROLE_*} authorities.
 */
class AuthFixtureRealmTest {

    private static AuthFixture fixture;

    @BeforeAll
    static void startKeycloak() {
        fixture = new AuthFixture();
    }

    @AfterAll
    static void stopKeycloak() {
        fixture.clean();
    }

    @Test
    void contentManagerTokenCarriesOnlyTheContentManagerRealmRole() {
        assertThat(realmRoles(fixture.contentManagerToken()))
                .contains("content-manager")
                .doesNotContain("sales");
    }

    @Test
    void salesTokenCarriesOnlyTheSalesRealmRole() {
        assertThat(realmRoles(fixture.salesToken()))
                .contains("sales")
                .doesNotContain("content-manager");
    }

    @SuppressWarnings("unchecked")
    private static List<String> realmRoles(String token) {
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        Map<String, Object> claims = JsonConfiguration.OBJECT_MAPPER.readValue(payload, Map.class);
        return (List<String>) ((Map<String, Object>) claims.get("realm_access")).get("roles");
    }
}
