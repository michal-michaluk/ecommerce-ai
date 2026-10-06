package com.example.offer.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Maps the Keycloak {@code realm_access.roles} claim onto Spring {@code ROLE_*} authorities,
 * so {@code hasRole("content-manager")} / {@code hasRole("sales")} match tokens minted by
 * the realm. Spring's default converter reads {@code scope}/{@code scp}, which Keycloak
 * does not populate with realm roles.
 */
public final class KeycloakRealmRoles implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String REALM_ACCESS = "realm_access";
    private static final String ROLES = "roles";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        if (!(jwt.getClaim(REALM_ACCESS) instanceof Map<?, ?> realmAccess)
                || !(realmAccess.get(ROLES) instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .map(String::valueOf)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(SecurityRoles.ROLE_PREFIX + role))
                .toList();
    }
}
