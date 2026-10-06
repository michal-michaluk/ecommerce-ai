package com.example.offer.security;

/**
 * The two element-02 roles and the Spring authority naming they map to. Roles reach the
 * endpoint families the frontend API declares: {@link #CONTENT_MANAGER} the content
 * surfaces, {@link #SALES} prices and discounts.
 */
public final class SecurityRoles {

    public static final String CONTENT_MANAGER = "content-manager";
    public static final String SALES = "sales";
    public static final String ROLE_PREFIX = "ROLE_";

    private SecurityRoles() {
    }
}
