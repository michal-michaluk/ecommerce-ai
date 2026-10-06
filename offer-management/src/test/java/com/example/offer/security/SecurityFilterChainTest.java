package com.example.offer.security;

import com.example.offer.tools.JsonConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * The assembled filter chain: 401 without a token, 403 for the wrong role, both carrying
 * the element-02 {@link com.example.offer.tools.ApiError} body, and the role matrix of
 * {@link SecurityRules} reaching the representative endpoint families.
 */
@SpringJUnitWebConfig(classes = {SecurityFilterChainTest.SecurityTestConfig.class,
        SecurityConfiguration.class, JsonConfiguration.class})
class SecurityFilterChainTest {

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void missingTokenIsUnauthenticatedWithContractBody() throws Exception {
        mvc.perform(get("/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("Authentication is required."));
    }

    @Test
    void unmatchedPathWithoutTokenIsUnauthenticated() throws Exception {
        mvc.perform(get("/no-such-endpoint"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void wrongRoleIsForbiddenWithContractBody() throws Exception {
        mvc.perform(get("/products").with(jwt().authorities(authority(SecurityRoles.SALES))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("The role is not allowed to perform this operation."));
    }

    @Test
    void plainUserRoleIsForbiddenOnContentEndpoint() throws Exception {
        mvc.perform(get("/products").with(jwt().authorities(authority("user"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void contentManagerReachesContentEndpoint() throws Exception {
        mvc.perform(get("/products").with(jwt().authorities(authority(SecurityRoles.CONTENT_MANAGER))))
                .andExpect(status().isOk())
                .andExpect(content().string("products"));
    }

    @Test
    void salesReachesPriceEndpoint() throws Exception {
        mvc.perform(get("/products/abc/prices").with(jwt().authorities(authority(SecurityRoles.SALES))))
                .andExpect(status().isOk())
                .andExpect(content().string("prices"));
    }

    @Test
    void contentManagerCannotReachPriceEndpoint() throws Exception {
        mvc.perform(get("/products/abc/prices").with(jwt().authorities(authority(SecurityRoles.CONTENT_MANAGER))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void salesCannotReachContentEndpoint() throws Exception {
        mvc.perform(get("/products/abc/prices").with(jwt().authorities(authority(SecurityRoles.SALES))))
                .andExpect(status().isOk());
        mvc.perform(get("/products").with(jwt().authorities(authority(SecurityRoles.SALES))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anyAuthenticatedRoleReachesPhotoFormats() throws Exception {
        mvc.perform(get("/product-photo-formats").with(jwt().authorities(authority(SecurityRoles.SALES))))
                .andExpect(status().isOk())
                .andExpect(content().string("formats"));
    }

    private static SimpleGrantedAuthority authority(String role) {
        return new SimpleGrantedAuthority(SecurityRoles.ROLE_PREFIX + role);
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    static class SecurityTestConfig {

        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }

        /** Never invoked: the {@code jwt()} request post-processor supplies the authentication. */
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new JwtException("test decoder is not used");
            };
        }
    }

    @RestController
    static class ProbeController {

        @GetMapping("/products")
        String products() {
            return "products";
        }

        @GetMapping("/products/{productId}/prices")
        String prices(@PathVariable String productId) {
            return "prices";
        }

        @GetMapping("/product-photo-formats")
        String formats() {
            return "formats";
        }
    }
}
