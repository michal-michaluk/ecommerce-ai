package com.example.offer.security;

import org.junit.jupiter.api.Test;

import static com.example.offer.security.SecurityRules.Access.AUTHENTICATED;
import static com.example.offer.security.SecurityRules.Access.CONTENT_MANAGER;
import static com.example.offer.security.SecurityRules.Access.PUBLIC;
import static com.example.offer.security.SecurityRules.Access.SALES;
import static org.assertj.core.api.Assertions.assertThat;

/** The element-02 authorization decision table, resolved per endpoint family. */
class SecurityRulesTest {

    @Test
    void actuatorHealthAndInfoArePublic() {
        assertThat(SecurityRules.requiredAccess("/actuator/health/liveness")).isEqualTo(PUBLIC);
        assertThat(SecurityRules.requiredAccess("/actuator/health/readiness")).isEqualTo(PUBLIC);
        assertThat(SecurityRules.requiredAccess("/actuator/info")).isEqualTo(PUBLIC);
    }

    @Test
    void contentManagerReachesProductsDraftsPhotosReviewsPublicationVersionsRemoval() {
        assertThat(SecurityRules.requiredAccess("/products")).isEqualTo(CONTENT_MANAGER);
        assertThat(SecurityRules.requiredAccess("/products/abc")).isEqualTo(CONTENT_MANAGER);
        assertThat(SecurityRules.requiredAccess("/products/abc/description-draft")).isEqualTo(CONTENT_MANAGER);
        assertThat(SecurityRules.requiredAccess("/products/abc/description-draft/review-requests")).isEqualTo(CONTENT_MANAGER);
        assertThat(SecurityRules.requiredAccess("/products/abc/description-draft/photos")).isEqualTo(CONTENT_MANAGER);
        assertThat(SecurityRules.requiredAccess("/products/abc/description-draft/photos/p1")).isEqualTo(CONTENT_MANAGER);
        assertThat(SecurityRules.requiredAccess("/review-requests")).isEqualTo(CONTENT_MANAGER);
        assertThat(SecurityRules.requiredAccess("/review-requests/rr-1")).isEqualTo(CONTENT_MANAGER);
        assertThat(SecurityRules.requiredAccess("/review-requests/rr-1/approval")).isEqualTo(CONTENT_MANAGER);
        assertThat(SecurityRules.requiredAccess("/products/abc/publication")).isEqualTo(CONTENT_MANAGER);
        assertThat(SecurityRules.requiredAccess("/products/abc/publications")).isEqualTo(CONTENT_MANAGER);
        assertThat(SecurityRules.requiredAccess("/products/abc/versions")).isEqualTo(CONTENT_MANAGER);
        assertThat(SecurityRules.requiredAccess("/products/abc/versions/2/revert")).isEqualTo(CONTENT_MANAGER);
        assertThat(SecurityRules.requiredAccess("/products/abc/offer-presence")).isEqualTo(CONTENT_MANAGER);
    }

    @Test
    void salesReachesPricesAndDiscounts() {
        assertThat(SecurityRules.requiredAccess("/products/abc/prices")).isEqualTo(SALES);
        assertThat(SecurityRules.requiredAccess("/products/abc/prices/p1")).isEqualTo(SALES);
    }

    @Test
    void photoFormatsAreAuthenticatedButNotRoleRestricted() {
        assertThat(SecurityRules.requiredAccess("/product-photo-formats")).isEqualTo(AUTHENTICATED);
    }

    @Test
    void nonHealthActuatorEndpointsAreExplicitlyAuthenticated() {
        assertThat(SecurityRules.requiredAccess("/actuator/env")).isEqualTo(AUTHENTICATED);
        assertThat(SecurityRules.requiredAccess("/actuator/prometheus")).isEqualTo(AUTHENTICATED);
    }

    @Test
    void unmatchedPathFallsBackToAuthenticated() {
        assertThat(SecurityRules.requiredAccess("/no-such-endpoint")).isEqualTo(AUTHENTICATED);
    }

    @Test
    void priceRulesPrecedeTheBroaderProductsRule() {
        int price = indexOf("/products/*/prices");
        int products = indexOf("/products/**");
        assertThat(price).isLessThan(products);
    }

    private static int indexOf(String pattern) {
        for (int i = 0; i < SecurityRules.RULES.size(); i++) {
            if (SecurityRules.RULES.get(i).pattern().equals(pattern)) {
                return i;
            }
        }
        throw new AssertionError("no rule for " + pattern);
    }
}
