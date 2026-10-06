package com.example.offer.pricing;

import com.example.offer.ControllerTestSupport;
import com.example.offer.IntegrationTest;
import com.example.offer.TestSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;

import static org.springframework.http.MediaType.APPLICATION_JSON;

@IntegrationTest
@Import(TestSecurityConfiguration.class)
class PricingControllerTest extends ControllerTestSupport {

    private static final String AUTH = HttpHeaders.AUTHORIZATION;
    private static final String UNKNOWN = "p-does-not-exist";

    @Autowired
    ObjectMapper json;

    @Autowired
    Clock clock;

    @Test
    void scheduleListChangeAndDeleteAPrice() throws Exception {
        String productId = newProduct();
        LocalDate from = LocalDate.now(clock).plusDays(1);
        byte[] priceId = schedule(productId, Map.of(
                "kind", "PRICE",
                "amount", Map.of("value", "259.00", "currency", "PLN"),
                "validFrom", from.toString(),
                "validTo", from.plusDays(30).toString()))
                .expectBody()
                .jsonPath("$.state").isEqualTo("SCHEDULED")
                .jsonPath("$.amount.value").isEqualTo("259.00")
                .jsonPath("$.amount.currency").isEqualTo("PLN")
                .jsonPath("$.percent").isEmpty()
                .returnResult().getResponseBody();
        String id = json.readTree(priceId).get("priceId").asString();

        client.get().uri("/products/{id}/prices", productId)
                .header(AUTH, bearer(SALES))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.items[0].kind").isEqualTo("PRICE")
                .jsonPath("$.items[0].priceId").isEqualTo(id);

        client.put().uri("/products/{id}/prices/{priceId}", productId, id)
                .header(AUTH, bearer(SALES))
                .contentType(APPLICATION_JSON)
                .bodyValue(Map.of("amount", Map.of("value", "269.00", "currency", "PLN"),
                        "validFrom", from.toString(), "validTo", from.plusDays(30).toString()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.amount.value").isEqualTo("269.00");

        client.delete().uri("/products/{id}/prices/{priceId}", productId, id)
                .header(AUTH, bearer(SALES))
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void scheduleADiscount() throws Exception {
        LocalDate from = LocalDate.now(clock).plusDays(1);
        schedule(newProduct(), Map.of("kind", "DISCOUNT", "percent", "10",
                "validFrom", from.toString(), "validTo", from.plusDays(30).toString()))
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.kind").isEqualTo("DISCOUNT")
                .jsonPath("$.percent").isEqualTo("10")
                .jsonPath("$.amount").isEmpty()
                .jsonPath("$.state").isEqualTo("SCHEDULED");
    }

    @Test
    void validToBeforeValidFromIsUnprocessable() throws Exception {
        LocalDate from = LocalDate.now(clock).plusDays(30);
        schedule(newProduct(), Map.of("kind", "PRICE",
                "amount", Map.of("value", "259.00", "currency", "PLN"),
                "validFrom", from.toString(), "validTo", from.minusDays(20).toString()))
                .expectStatus().isEqualTo(422)
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_DATE_RANGE")
                .jsonPath("$.message").isNotEmpty();
    }

    @Test
    void overlappingRangeIsAConflict() throws Exception {
        String productId = newProduct();
        LocalDate from = LocalDate.now(clock).plusDays(1);
        schedule(productId, Map.of("kind", "PRICE",
                "amount", Map.of("value", "259.00", "currency", "PLN"),
                "validFrom", from.toString(), "validTo", from.plusDays(30).toString()))
                .expectStatus().isCreated();

        schedule(productId, Map.of("kind", "PRICE",
                "amount", Map.of("value", "299.00", "currency", "PLN"),
                "validFrom", from.plusDays(15).toString(), "validTo", from.plusDays(45).toString()))
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("PRICE_OVERLAP");
    }

    @Test
    void editingAnActiveEntryIsRejected() throws Exception {
        String productId = newProduct();
        LocalDate today = LocalDate.now(clock);
        byte[] body = schedule(productId, Map.of("kind", "PRICE",
                "amount", Map.of("value", "259.00", "currency", "PLN"),
                "validFrom", today.minusDays(10).toString(),
                "validTo", today.plusDays(10).toString()))
                .expectStatus().isCreated()
                .expectBody()
                .returnResult().getResponseBody();
        String id = json.readTree(body).get("priceId").asString();

        client.put().uri("/products/{id}/prices/{priceId}", productId, id)
                .header(AUTH, bearer(SALES))
                .contentType(APPLICATION_JSON)
                .bodyValue(Map.of("amount", Map.of("value", "269.00", "currency", "PLN"),
                        "validFrom", today.toString(), "validTo", today.plusDays(20).toString()))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("DRAFT_NOT_EDITABLE")
                .jsonPath("$.message").isEqualTo("Only a scheduled price entry can be changed or deleted.");
    }

    @Test
    void unknownProductIsNotFound() {
        client.get().uri("/products/{id}/prices", UNKNOWN)
                .header(AUTH, bearer(SALES))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("PRODUCT_NOT_FOUND");

        schedule(UNKNOWN, Map.of("kind", "PRICE",
                "amount", Map.of("value", "259.00", "currency", "PLN"),
                "validFrom", LocalDate.now(clock).plusDays(1).toString()))
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("PRODUCT_NOT_FOUND");
    }

    @Test
    void missingTokenIsUnauthenticated() {
        client.get().uri("/products/{id}/prices", UNKNOWN).exchange().expectStatus().isUnauthorized();
    }

    @Test
    void contentManagerMayNotSetPrices() {
        client.get().uri("/products/{id}/prices", UNKNOWN)
                .header(AUTH, bearer(CONTENT_MANAGER))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    private String newProduct() {
        byte[] body = client.post().uri("/products")
                .header(AUTH, bearer(CONTENT_MANAGER))
                .contentType(APPLICATION_JSON)
                .bodyValue(Map.of("title", "Kosiarka", "category", "Ogród"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody().returnResult().getResponseBody();
        return json.readTree(body).get("productId").asString();
    }

    private WebTestClient.ResponseSpec schedule(String productId, Map<String, Object> body) {
        return client.post().uri("/products/{id}/prices", productId)
                .header(AUTH, bearer(SALES))
                .contentType(APPLICATION_JSON)
                .bodyValue(body)
                .exchange();
    }
}
