package com.example.offer.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRealmRolesTest {

    private final KeycloakRealmRoles converter = new KeycloakRealmRoles();

    @Test
    void mapsRealmRolesToRoleAuthorities() {
        Jwt jwt = jwt(Map.of("realm_access", Map.of("roles", List.of("content-manager", "offline_access"))));

        assertThat(converter.convert(jwt)).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_content-manager", "ROLE_offline_access");
    }

    @Test
    void salesRoleMapsToSalesAuthority() {
        Jwt jwt = jwt(Map.of("realm_access", Map.of("roles", List.of("sales"))));

        assertThat(converter.convert(jwt)).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_sales");
    }

    @Test
    void tokenWithoutRealmAccessHasNoAuthorities() {
        assertThat(converter.convert(jwt(Map.of("sub", "carla")))).isEmpty();
    }

    @Test
    void malformedRealmAccessHasNoAuthorities() {
        assertThat(converter.convert(jwt(Map.of("realm_access", "not-a-map")))).isEmpty();
        assertThat(converter.convert(jwt(Map.of("realm_access", Map.of("roles", "not-a-collection"))))).isEmpty();
    }

    private static Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "none");
        claims.forEach(builder::claim);
        return builder.build();
    }
}
