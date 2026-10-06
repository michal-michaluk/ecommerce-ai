package com.example.offer.tools;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiErrorAdviceTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new ApiErrorAdvice())
                .setValidator(validator)
                .build();
    }

    @Test
    void bodyValidationFailureIs422AndNamesFields() throws Exception {
        mvc.perform(post("/probe/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Request validation failed."))
                .andExpect(jsonPath("$.details.fields", contains("title")));
    }

    @Test
    void unexpectedFailureIs500WithoutLeakingTheExceptionMessage() throws Exception {
        mvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Unexpected error."))
                .andExpect(content().string(not(containsString("secret-token-xyz"))));
    }

    @Test
    void responseStatusExceptionKeepsItsStatus() throws Exception {
        mvc.perform(get("/probe/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void statusWithoutAContractCodeKeepsItsStatusAndCarriesNoContradictingCode() {
        ResponseEntity<ApiError> response =
                new ApiErrorAdvice().onUnexpected(new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void serverErrorStatusMapsToInternalError() {
        ResponseEntity<ApiError> response =
                new ApiErrorAdvice().onUnexpected(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.INTERNAL_ERROR);
    }

    @Test
    void unmatchedRouteIs404InTheContractShape() {
        ResponseEntity<ApiError> response =
                new ApiErrorAdvice().onUnmatchedRoute(
                        new NoResourceFoundException(HttpMethod.GET, "/no-such-route", null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.NOT_FOUND);
    }

    @RestController
    static class ProbeController {

        @PostMapping("/probe/body")
        void body(@RequestBody @Valid Payload payload) {
        }

        @GetMapping("/probe/boom")
        void boom() {
            throw new IllegalStateException("secret-token-xyz");
        }

        @GetMapping("/probe/missing")
        void missing() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    record Payload(@NotBlank String title) {
    }
}
