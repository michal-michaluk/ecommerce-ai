package com.example.offer.security;

import com.example.offer.tools.ApiError;
import com.example.offer.tools.JsonConfiguration;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Writes an element-02 {@link ApiError} body with the status its {@code code} declares. */
final class ApiErrorResponse {

    private static final String CONTENT_TYPE = "application/json";

    private ApiErrorResponse() {
    }

    static void write(HttpServletResponse response, ApiError error) throws IOException {
        response.setStatus(error.code().status().value());
        response.setContentType(CONTENT_TYPE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        JsonConfiguration.OBJECT_MAPPER.writeValue(response.getOutputStream(), error);
    }
}
