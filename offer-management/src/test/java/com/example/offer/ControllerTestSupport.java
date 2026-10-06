package com.example.offer;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Shared HTTP client wiring for the controller integration tests. */
public abstract class ControllerTestSupport {

    protected static final String CONTENT_MANAGER = "a.kowalska~content-manager";
    protected static final String REVIEWER = "m.nowak~content-manager";
    protected static final String SALES = "s.zielinski~sales";

    @LocalServerPort
    protected int port;

    protected WebTestClient client;

    @BeforeEach
    void setUpClient() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}
