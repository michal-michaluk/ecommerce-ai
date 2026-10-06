package com.example.offer.security;

import com.example.offer.tools.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * Emits the element-02 {@code 401 UNAUTHENTICATED} body on a missing or invalid bearer
 * token, instead of Spring Security's empty {@code WWW-Authenticate} response.
 */
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException failure)
            throws IOException {
        ApiErrorResponse.write(response, ApiError.unauthenticated());
    }
}
