package com.example.offer.security;

import com.example.offer.tools.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/**
 * Emits the element-02 {@code 403 FORBIDDEN} body when the authenticated role does not
 * reach the endpoint, instead of Spring Security's empty response.
 */
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException failure)
            throws IOException {
        ApiErrorResponse.write(response, ApiError.forbidden());
    }
}
