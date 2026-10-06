package com.example.offer.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;

/**
 * The element-02 authorization decision table — the role model, independent of any
 * controller. {@link #authorize} applies it to the filter chain; {@link #requiredAccess}
 * resolves the decision for a path so the model is testable without a web context.
 *
 * <p>Order matters: a pattern nested in a broader one must precede it — the price
 * patterns are declared before {@code /products/**}.
 */
public final class SecurityRules {

    public enum Access {
        PUBLIC,
        AUTHENTICATED,
        CONTENT_MANAGER,
        SALES
    }

    public record Rule(String pattern, Access access) {
    }

    /** Ordered request matcher rules; any request not matching one requires authentication. */
    public static final List<Rule> RULES = List.of(
            new Rule("/actuator/health/**", Access.PUBLIC),
            new Rule("/actuator/info", Access.PUBLIC),
            new Rule("/actuator/**", Access.AUTHENTICATED),
            new Rule("/products/*/prices", Access.SALES),
            new Rule("/products/*/prices/**", Access.SALES),
            new Rule("/product-photo-formats", Access.AUTHENTICATED),
            new Rule("/products", Access.CONTENT_MANAGER),
            new Rule("/products/**", Access.CONTENT_MANAGER),
            new Rule("/review-requests", Access.CONTENT_MANAGER),
            new Rule("/review-requests/**", Access.CONTENT_MANAGER)
    );

    private static final PathPatternParser PARSER = new PathPatternParser();

    private SecurityRules() {
    }

    static void authorize(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        for (Rule rule : RULES) {
            var matcher = pathPattern(rule.pattern());
            switch (rule.access()) {
                case PUBLIC -> auth.requestMatchers(matcher).permitAll();
                case AUTHENTICATED -> auth.requestMatchers(matcher).authenticated();
                case CONTENT_MANAGER -> auth.requestMatchers(matcher).hasRole(SecurityRoles.CONTENT_MANAGER);
                case SALES -> auth.requestMatchers(matcher).hasRole(SecurityRoles.SALES);
            }
        }
        auth.anyRequest().authenticated();
    }

    /** The access the ordered rules assign to {@code path}; the unmatched default is {@link Access#AUTHENTICATED}. */
    public static Access requiredAccess(String path) {
        PathContainer container = PathContainer.parsePath(path);
        return RULES.stream()
                .filter(rule -> PARSER.parse(rule.pattern()).matches(container))
                .map(Rule::access)
                .findFirst()
                .orElse(Access.AUTHENTICATED);
    }
}
