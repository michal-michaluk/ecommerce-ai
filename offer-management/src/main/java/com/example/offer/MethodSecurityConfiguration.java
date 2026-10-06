package com.example.offer;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Enables the endpoint-level {@code @PreAuthorize} role guards of the element-02 auth table.
 * Kept beside the framework wiring so the bounded contexts stay free of security configuration.
 */
@Configuration
@EnableMethodSecurity
class MethodSecurityConfiguration {
}
